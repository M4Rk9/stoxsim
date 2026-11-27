#!/usr/bin/env python3
"""Deployment-only provider check. Never logs keys, response bodies, or exception text."""
import json
import os
import re
import ssl
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

def validate_settings(env):
    for name in ("PLUS", "PRO"):
        value = env.get("FINWIZ_" + name + "_CAPACITY_PERCENT", "25")
        if not re.fullmatch(r"[0-9]{1,3}", value):
            raise ConfigError("Invalid tier capacity share: " + name)
    if sum(int(env.get("FINWIZ_" + tier + "_CAPACITY_PERCENT","25")) for tier in ("PLUS","PRO")) > 100:
        raise ConfigError("Plus and Pro capacity shares exceed 100 percent")
    if flag(env,"FINWIZ_PAID_ENABLED"):
        if not flag(env,"FINWIZ_PAID_APPROVED"):
            raise ConfigError("Paid Finwiz approval required")
        if required(env,"FINWIZ_PAID_GEMINI_API_KEY") == env.get("GEMINI_API_KEY","").strip():
            raise ConfigError("Paid Gemini requires a separate provider key")
        if not re.fullmatch(r"[A-Za-z0-9._-]{1,128}", required(env,"FINWIZ_PAID_GEMINI_MODEL")):
            raise ConfigError("Invalid paid Gemini model")
        for suffix in ("INPUT_MICROS_PER_MILLION","OUTPUT_MICROS_PER_MILLION","REASONING_MICROS_PER_MILLION",
                       "DAILY_MICROS","MONTHLY_MICROS","PLUS_MONTHLY_MICROS","PRO_MONTHLY_MICROS","USER_DAILY_MICROS",
                       "REQUESTS_PER_MINUTE","TOKENS_PER_MINUTE","TOKENS_PER_DAY","DAILY_ATTEMPTS"):
            if not re.fullmatch(r"[0-9]{1,15}", required(env,"FINWIZ_PAID_" + suffix)) or int(env["FINWIZ_PAID_" + suffix]) == 0:
                raise ConfigError("Invalid paid Finwiz setting: " + suffix)
        if int(env["FINWIZ_PAID_DAILY_MICROS"]) > int(env["FINWIZ_PAID_MONTHLY_MICROS"]):
            raise ConfigError("Paid daily budget exceeds monthly budget")
        if int(env["FINWIZ_PAID_TOKENS_PER_MINUTE"]) > int(env["FINWIZ_PAID_TOKENS_PER_DAY"]):
            raise ConfigError("Paid minute token limit exceeds daily limit")

def candidates(env):
    if not flag(env, "FINWIZ_AI_ENABLED", "true"):
        return []
    result = []
    for name in ("GROQ", "GEMINI", "CLOUDFLARE", "OPENROUTER"):
        prefix = "FINWIZ_" + name
        if name == "GEMINI":
            if not env.get("GEMINI_API_KEY", "").strip() or not positive(env, prefix+"_DAILY_LIMIT", 500):
                continue
            if not flag(env, prefix+"_FREE_CAPACITY_CONFIRMED"):
                raise ConfigError("Free capacity not confirmed: GEMINI")
            for suffix, default in (("_REQUESTS_PER_MINUTE",10),("_TOKENS_PER_MINUTE",40000),("_TOKENS_PER_DAY",1000000)):
                if not positive(env,prefix+suffix,default):
                    raise ConfigError("Enabled provider has zero admission capacity: GEMINI")
            key = required(env, "GEMINI_API_KEY")
            model = env.get("GEMINI_MODEL", "gemini-3.6-flash")
            if not re.fullmatch(r"[A-Za-z0-9._-]{1,128}", model):
                raise ConfigError("Invalid Gemini model")
            base = env.get("GEMINI_BASE_URL", "https://generativelanguage.googleapis.com")
            parts = urlsplit(base)
            if parts.scheme != "https" or parts.netloc != "generativelanguage.googleapis.com" or parts.path not in ("","/") or parts.query or parts.fragment:
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
        if not re.fullmatch(r"[A-Za-z0-9@._:/-]{1,128}",model):
            raise ConfigError("Invalid model: " + name)
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
                return "invalid","oversize response"
            return ("healthy","complete response") if complete(name,json.loads(raw)) else ("invalid","incomplete or rejected response")
    except urllib.error.HTTPError as e:
        if e.code in (401,403,404):
            return "permission",f"provider authentication, model or permission rejection (HTTP {e.code})"
        if e.code in (408,429) or 500 <= e.code <= 599:
            return "transient",f"temporary provider error (HTTP {e.code})"
        return "invalid",f"unexpected HTTP {e.code}"
    except urllib.error.URLError as e:
        if isinstance(e.reason, ssl.SSLError):
            return "invalid","TLS verification failed"
        return "transient","transport failure"
    except TimeoutError:
        return "transient","request timed out"
    except Exception:
        return "invalid","invalid response"

def run(env, opener=None):
    try:
        validate_settings(env)
        configured=candidates(env)
    except (ConfigError, ValueError) as e:
        message = str(e) if isinstance(e, ConfigError) else "Invalid Finwiz configuration value"
        print("Finwiz configuration rejected: "+message)
        return 1
    if not configured:
        print("Finwiz explicitly disabled")
        return 0
    opener=opener or urllib.request.build_opener(NoRedirect())
    outcomes=[]
    for candidate in configured:
        outcome,reason=probe(candidate,opener)
        print(candidate[0]+": "+outcome+" ("+reason+")")
        outcomes.append(outcome)
    healthy=outcomes.count("healthy")
    if healthy:
        print(f"Finwiz preflight passed: {healthy}/{len(configured)} configured providers")
        return 0
    if all(outcome == "transient" for outcome in outcomes) and env.get("FINWIZ_DEGRADED_ACK") == "ACCEPT_FINWIZ_DEGRADED":
        message = "Finwiz degraded deployment acknowledged: all free providers reported temporary failures"
        print(message)
        if summary := env.get("GITHUB_STEP_SUMMARY"):
            with open(summary,"a",encoding="utf-8") as output:
                print(message,file=output)
        return 0
    print("Finwiz preflight failed: no configured provider returned a complete response")
    return 1

if __name__=="__main__":
    raise SystemExit(run(os.environ))
