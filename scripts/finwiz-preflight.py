#!/usr/bin/env python3
"""Deployment-only provider check. Never logs keys, response bodies, or exception text."""
import json
import os
import re
import urllib.request
import urllib.error
from urllib.parse import urlsplit

PROMPT = "Reply with exactly FINWIZ_OK."
MARKER = "FINWIZ_OK"

class ConfigError(Exception):
    pass

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

def flag(env, key, default="false"):
    value = env.get(key, default)
    if value not in ("true", "false"):
        raise ConfigError("Invalid boolean: " + key)
    return value == "true"

def positive(env, key, default):
    value = env.get(key, str(default))
    if not re.fullmatch(r"[0-9]{1,9}", value):
        raise ConfigError("Invalid numeric setting: " + key)
    return int(value) > 0

def required(env, key):
    value = env.get(key, "").strip()
    if not value or "\n" in value or "\r" in value:
        raise ConfigError("Missing or invalid setting: " + key)
    return value

def candidates(env):
    if not flag(env, "FINWIZ_AI_ENABLED", "true"):
        return []
    result = []
    for name in ("GROQ", "GEMINI", "CLOUDFLARE", "OPENROUTER"):
        prefix = "FINWIZ_" + name
        if name == "GEMINI":
            if not env.get("GEMINI_API_KEY", "").strip() or not positive(env, prefix+"_DAILY_LIMIT", 500):
                continue
            key = required(env, "GEMINI_API_KEY")
            model = env.get("GEMINI_MODEL", "gemini-3.6-flash")
            if not re.fullmatch(r"[A-Za-z0-9._-]{1,128}", model):
                raise ConfigError("Invalid Gemini model")
            base = env.get("GEMINI_BASE_URL", "https://generativelanguage.googleapis.com")
            parts = urlsplit(base)
            if parts.scheme != "https" or not parts.hostname or parts.username or parts.password or parts.query or parts.fragment:
                raise ConfigError("Invalid Gemini base URL")
            thinking = env.get("FINWIZ_THINKING_LEVEL", "minimal")
            if thinking not in ("minimal", "low", "medium", "high"):
                raise ConfigError("Invalid Gemini thinking level")
            body = {"contents":[{"role":"user","parts":[{"text":PROMPT}]}],
                    "generationConfig":{"maxOutputTokens":256,"thinkingConfig":{"thinkingLevel":thinking}}}
            result.append((name, base.rstrip("/")+"/v1beta/models/"+model+":generateContent", {"x-goog-api-key":key}, body))
            continue
        if not flag(env, prefix+"_ENABLED"):
            continue
        if not flag(env, prefix+"_FREE_CAPACITY_CONFIRMED"):
            raise ConfigError("Free capacity not confirmed: " + name)
        for suffix, default in (("_DAILY_LIMIT", 100), ("_REQUESTS_PER_MINUTE", 5), ("_TOKENS_PER_MINUTE", 8000), ("_TOKENS_PER_DAY", 180000)):
            if not positive(env, prefix+suffix, default):
                raise ConfigError("Enabled provider has zero admission capacity: " + name)
        if name == "GROQ":
            key = required(env, "GROQ_API_KEY")
            model = env.get("GROQ_MODEL", "openai/gpt-oss-20b")
            url = "https://api.groq.com/openai/v1/chat/completions"
        elif name == "CLOUDFLARE":
            key = required(env, "CLOUDFLARE_API_TOKEN")
            account = required(env, "CLOUDFLARE_ACCOUNT_ID")
            if not re.fullmatch(r"[A-Za-z0-9_-]{1,128}", account):
                raise ConfigError("Invalid Cloudflare account")
            model = env.get("CLOUDFLARE_AI_MODEL", "@cf/openai/gpt-oss-20b")
            url = "https://api.cloudflare.com/client/v4/accounts/"+account+"/ai/v1/chat/completions"
        else:
            key = required(env, "OPENROUTER_API_KEY")
            model = required(env, "OPENROUTER_MODEL")
            upstream = required(env, "OPENROUTER_UPSTREAM")
            if not model.endswith(":free") or not re.fullmatch(r"[A-Za-z0-9_-]{1,80}", upstream) or upstream.lower() in {"groq","google","google-ai-studio","google-vertex","cloudflare"}:
                raise ConfigError("OpenRouter requires a free model and independent upstream")
            url = "https://openrouter.ai/api/v1/chat/completions"
        if not model.strip():
            raise ConfigError("Missing model: " + name)
        body = {"model":model,"messages":[{"role":"user","content":PROMPT}],
                "stream":False, "max_completion_tokens" if name=="GROQ" else "max_tokens":256}
        if name=="GROQ" and model.startswith("openai/gpt-oss"):
            body["reasoning_effort"]="low"
        if name=="OPENROUTER":
            body["provider"]={"only":[upstream],"allow_fallbacks":False,"data_collection":"deny","max_price":{"prompt":0,"completion":0}}
        result.append((name,url,{"Authorization":"Bearer "+key},body))
    if not result:
        raise ConfigError("Finwiz is enabled but no provider is configured")
    return result

def complete(name, payload):
    if name=="GEMINI":
        if payload.get("promptFeedback",{}).get("blockReason"):
            return False
        return any(c.get("finishReason")=="STOP" and any(not p.get("thought") and MARKER in str(p.get("text","")) for p in c.get("content",{}).get("parts",[])) for c in payload.get("candidates",[]))
    return any(c.get("finish_reason")=="stop" and not c.get("message",{}).get("refusal") and MARKER in str(c.get("message",{}).get("content","")) for c in payload.get("choices",[]))

def probe(candidate, opener):
    name,url,headers,body=candidate
    request=urllib.request.Request(url,data=json.dumps(body).encode(),headers={**headers,"Content-Type":"application/json"},method="POST")
    try:
        with opener.open(request,timeout=12) as response:
            raw=response.read(262145)
            if len(raw)>262144:
                return False,"oversize"
            return complete(name,json.loads(raw)), "response"
    except urllib.error.HTTPError as e:
        return False,"HTTP "+str(e.code)
    except Exception:
        return False,"transport or invalid response"

def run(env, opener=None):
    try:
        configured=candidates(env)
    except ConfigError as e:
        print("Finwiz configuration rejected: "+str(e))
        return 1
    if not configured:
        print("Finwiz explicitly disabled")
        return 0
    opener=opener or urllib.request.build_opener(NoRedirect())
    # One small probe per enabled provider, no retries. At least one must produce a complete response.
    healthy=0
    for candidate in configured:
        ok,reason=probe(candidate,opener)
        print(candidate[0]+": "+("passed" if ok else "unavailable ("+reason+")"))
        healthy+=int(ok)
    if not healthy:
        print("Finwiz preflight failed: no enabled provider returned a complete response")
        return 1
    print("Finwiz preflight passed: "+str(healthy)+"/"+str(len(configured))+" configured providers")
    return 0

if __name__=="__main__":
    raise SystemExit(run(os.environ))
