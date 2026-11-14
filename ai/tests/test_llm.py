import io
import json
import os
import unittest
from unittest.mock import patch

from doran_ai import DoranAgent
from doran_ai.llm import ChatCompletionsClassifier


class ClassifierTests(unittest.TestCase):
    def test_remote_https_and_key_are_required(self):
        remote = ChatCompletionsClassifier("https://openrouter.ai/api/v1/chat/completions", "open-model:free", "test-key")
        self.assertEqual(remote.timeout, 12.0)
        with self.assertRaises(ValueError):
            ChatCompletionsClassifier("http://127.0.0.1:11434/v1/chat/completions", "open-model:free", "test-key")
        with self.assertRaises(ValueError):
            ChatCompletionsClassifier("http://example.com/v1/chat/completions", "open-model:free", "test-key")
        with self.assertRaises(ValueError):
            ChatCompletionsClassifier("https://provider.example/v1/chat/completions", "open-model:free", "")

    def test_environment_sets_remote_model_and_timeout(self):
        settings = {
            "DORAN_LLM_ENDPOINT": "https://openrouter.ai/api/v1/chat/completions",
            "DORAN_LLM_MODEL": "open-model:free",
            "DORAN_LLM_API_KEY": "test-key",
            "DORAN_LLM_TIMEOUT_SECONDS": "15",
        }
        with patch.dict(os.environ, settings):
            classifier = ChatCompletionsClassifier.from_env()
        self.assertEqual(classifier.model, "open-model:free")
        self.assertEqual(classifier.timeout, 15.0)

    def test_request_sends_only_utterance_and_parses_allowlisted_intent(self):
        classifier = ChatCompletionsClassifier("https://openrouter.ai/api/v1/chat/completions", "open-model:free", "test-key")
        response = io.BytesIO(json.dumps({"choices": [{"message": {"content": '{"intent":"place"}'}}]}).encode())
        with patch("doran_ai.llm.request.urlopen", return_value=response) as urlopen:
            intent = classifier.classify("이 근방에 들를 곳을 추천해 줄래?")
        sent = urlopen.call_args.args[0]
        body = json.loads(sent.data)
        self.assertEqual(intent, "place")
        self.assertEqual(body["messages"][1]["content"], "이 근방에 들를 곳을 추천해 줄래?")
        self.assertEqual(set(body), {"model", "messages", "temperature"})
        self.assertEqual(sent.get_header("Authorization"), "Bearer test-key")

    def test_model_cannot_return_an_execution_command_as_intent(self):
        classifier = ChatCompletionsClassifier("https://openrouter.ai/api/v1/chat/completions", "open-model:free", "test-key")
        response = io.BytesIO(json.dumps({"choices": [{"message": {"content": '{"intent":"ADD_WAYPOINT"}'}}]}).encode())
        with patch("doran_ai.llm.request.urlopen", return_value=response):
            self.assertEqual(classifier.classify("경유지 추가해"), "unknown")

    def test_model_failure_returns_to_rule_based_response(self):
        class BrokenClassifier:
            def classify(self, utterance):
                raise TimeoutError("model timeout")

        result = DoranAgent(BrokenClassifier()).handle({
            "kind": "user_utterance", "now": "2026-11-07T12:00:00+09:00",
            "vehicle": {"gear": "P"}, "utterance": "이 근방에 들를 곳을 추천해 줄래?",
        })
        self.assertEqual(result["status"], "unavailable")
        self.assertEqual(result["commands"], [])

    def test_rewrite_sends_draft_only_and_keeps_verified_name(self):
        classifier = ChatCompletionsClassifier(
            "https://api.groq.com/openai/v1/chat/completions", "openai/gpt-oss-20b", "test-key"
        )
        response = io.BytesIO(json.dumps({
            "choices": [{"message": {"content": '{"speech":"바닷가 식당에 들러 볼까?"}'}}]
        }).encode())
        with patch("doran_ai.llm.request.urlopen", return_value=response) as urlopen:
            speech = classifier.rewrite("바닷가 식당을 찾았어. 들를까?", "근처 맛집 추천해 줘", ["바닷가 식당"])
        body = json.loads(urlopen.call_args.args[0].data)
        self.assertEqual(speech, "바닷가 식당에 들러 볼까?")
        self.assertEqual(body["response_format"]["type"], "json_schema")
        self.assertTrue(body["response_format"]["json_schema"]["strict"])
        self.assertNotIn("latitude", json.dumps(body))

    def test_rewrite_rejects_missing_name_new_number_and_changed_question(self):
        classifier = ChatCompletionsClassifier(
            "https://api.groq.com/openai/v1/chat/completions", "openai/gpt-oss-20b", "test-key"
        )
        for output in ["다른 식당에 들러 볼까?", "바닷가 식당까지 5분이야?", "바닷가 식당에 들르자.", "바닷가 식당에 들러볼까요?"]:
            response = io.BytesIO(json.dumps({
                "choices": [{"message": {"content": json.dumps({"speech": output}, ensure_ascii=False)}}]
            }).encode())
            with self.subTest(output=output), patch("doran_ai.llm.request.urlopen", return_value=response):
                with self.assertRaises(ValueError):
                    classifier.rewrite("바닷가 식당에 들를까?", "근처 맛집 추천해 줘", ["바닷가 식당"])

    def test_groq_key_can_be_read_without_duplicate_llm_key(self):
        settings = {
            "DORAN_LLM_ENDPOINT": "https://api.groq.com/openai/v1/chat/completions",
            "DORAN_LLM_MODEL": "openai/gpt-oss-20b",
            "DORAN_LLM_API_KEY": "",
            "GROQ_API_KEY": "test-key",
        }
        with patch.dict(os.environ, settings):
            self.assertEqual(ChatCompletionsClassifier.from_env().api_key, "test-key")


if __name__ == "__main__":
    unittest.main()
