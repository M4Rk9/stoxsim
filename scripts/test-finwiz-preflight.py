#!/usr/bin/env python3
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import unittest
from unittest.mock import Mock
import urllib.error

spec=importlib.util.spec_from_file_location("preflight",Path(__file__).with_name("finwiz-preflight.py"))
mod=importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

class Response:
    def __init__(self,payload): self.payload=payload
    def __enter__(self): return self
    def __exit__(self,*args): pass
    def read(self,limit): return json.dumps(self.payload).encode()

class PreflightTests(unittest.TestCase):
    def invoke(self,env,opener):
        out=io.StringIO()
        with contextlib.redirect_stdout(out):
            result=mod.run(env,opener)
        return result,out.getvalue()
    def groq(self):
        return {"FINWIZ_GROQ_ENABLED":"true","FINWIZ_GROQ_FREE_CAPACITY_CONFIRMED":"true","GROQ_API_KEY":"synthetic-test-key"}
    def success(self):
        return Response({"choices":[{"finish_reason":"stop","message":{"content":"FINWIZ_OK"}}]})
    def test_groq_without_gemini(self):
        opener=Mock(); opener.open.return_value=self.success()
        status,logs=self.invoke(self.groq(),opener)
        self.assertEqual(status,0)
        self.assertNotIn("synthetic-test-key",logs)
        self.assertEqual(opener.open.call_args.args[0].full_url,"https://api.groq.com/openai/v1/chat/completions")
    def test_gemini_outage_does_not_block_healthy_groq(self):
        opener=Mock(); opener.open.side_effect=[self.success(),urllib.error.HTTPError("https://example.test",429,"synthetic-secret",None,None)]
        status,logs=self.invoke({**self.groq(),"GEMINI_API_KEY":"synthetic-secret","FINWIZ_GEMINI_FREE_CAPACITY_CONFIRMED":"true"},opener)
        self.assertEqual(status,0)
        self.assertNotIn("synthetic-secret",logs)
        self.assertEqual(opener.open.call_count,2)
    def test_all_unavailable_blocks_deploy(self):
        opener=Mock(); opener.open.return_value=Response({"choices":[{"finish_reason":"length","message":{"content":"FINWIZ_OK"}}]})
        self.assertEqual(self.invoke(self.groq(),opener)[0],1)
    def test_degraded_mode_requires_ack_and_only_accepts_transient_outages(self):
        opener=Mock()
        g = self.groq()
        opener.open.side_effect=urllib.error.HTTPError("https://example.test",503,"synthetic-secret",None,None)
        self.assertEqual(self.invoke(g,opener)[0],1)
        accepted={**g,"FINWIZ_DEGRADED_ACK":"ACCEPT_FINWIZ_DEGRADED"}
        status,logs=self.invoke(accepted,opener)
        self.assertEqual(status,0)
        self.assertIn("degraded deployment acknowledged",logs)
        self.assertNotIn("synthetic-secret",logs)
        opener.open.side_effect=urllib.error.HTTPError("https://example.test",403,"synthetic-secret",None,None)
        self.assertEqual(self.invoke(accepted,opener)[0],1)
        opener.open.side_effect=urllib.error.URLError(mod.ssl.SSLError("synthetic-secret"))
        self.assertEqual(self.invoke(accepted,opener)[0],1)
        opener.reset_mock()
        self.assertEqual(self.invoke({**accepted,"FINWIZ_PAID_ENABLED":"true"},opener)[0],1)
        self.assertEqual(self.invoke({**accepted,"GEMINI_API_KEY":"unconfirmed"},opener)[0],1)
        opener.open.assert_not_called()

    def test_unconfirmed_provider_does_not_dispatch(self):
        opener=Mock()
        self.assertEqual(self.invoke({**self.groq(),"FINWIZ_GROQ_FREE_CAPACITY_CONFIRMED":"false"},opener)[0],1)
        opener.open.assert_not_called()
    def test_disabled_does_not_dispatch(self):
        opener=Mock()
        self.assertEqual(self.invoke({"FINWIZ_AI_ENABLED":"false"},opener)[0],0)
        opener.open.assert_not_called()
    def test_no_provider_blocks_deploy(self):
        opener=Mock()
        self.assertEqual(self.invoke({},opener)[0],1)
        opener.open.assert_not_called()
    def test_openrouter_cannot_use_paid_model_or_same_upstream(self):
        env={"FINWIZ_OPENROUTER_ENABLED":"true","FINWIZ_OPENROUTER_FREE_CAPACITY_CONFIRMED":"true","OPENROUTER_API_KEY":"test-key","OPENROUTER_MODEL":"model:free","OPENROUTER_UPSTREAM":"groq"}
        opener=Mock()
        self.assertEqual(self.invoke(env,opener)[0],1)
        self.assertEqual(self.invoke({**env,"OPENROUTER_UPSTREAM":"independent","OPENROUTER_MODEL":"paid/model"},opener)[0],1)
        opener.open.assert_not_called()
    def test_gemini_only(self):
        opener=Mock(); opener.open.return_value=Response({"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"FINWIZ_OK"}]}}]})
        self.assertEqual(self.invoke({"GEMINI_API_KEY":"test-key","FINWIZ_GEMINI_FREE_CAPACITY_CONFIRMED":"true"},opener)[0],0)
    def test_refusal_is_not_success(self):
        opener=Mock(); opener.open.return_value=Response({"choices":[{"finish_reason":"stop","message":{"content":"FINWIZ_OK","refusal":"blocked"}}]})
        self.assertEqual(self.invoke(self.groq(),opener)[0],1)

if __name__=="__main__":
    unittest.main()
