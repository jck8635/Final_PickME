from types import SimpleNamespace
from unittest.mock import Mock
import pytest
from app.services import gemini, embedding_service as emb


def test_gemini_success(monkeypatch):
    client = Mock()
    client.models.generate_content.return_value = SimpleNamespace(text='결과')
    monkeypatch.setattr(gemini, '_get_client', lambda: client)
    assert gemini.generate('고민', models=['first']) == '결과'
    client.models.generate_content.assert_called_once()


def test_gemini_timeout_fallback(monkeypatch):
    client = Mock()
    client.models.generate_content.side_effect = [TimeoutError(), SimpleNamespace(text='결과')]
    monkeypatch.setattr(gemini, '_get_client', lambda: client)
    assert gemini.generate('고민', models=['first','second']) == '결과'
    assert [c.kwargs['model'] for c in client.models.generate_content.call_args_list] == ['first','second']


def test_gemini_retry_503(monkeypatch):
    class FakeAPIError(Exception):
        code = 503
    monkeypatch.setattr(gemini.errors, 'APIError', FakeAPIError)
    sleep = Mock()
    monkeypatch.setattr(gemini.time, 'sleep', sleep)
    client = Mock()
    client.models.generate_content.side_effect = [FakeAPIError(), SimpleNamespace(text='결과')]
    monkeypatch.setattr(gemini, '_get_client', lambda: client)
    assert gemini.generate('고민', models=['first']) == '결과'
    assert client.models.generate_content.call_count == 2
    sleep.assert_called_once()


def test_gemini_all_fail(monkeypatch):
    client = Mock()
    client.models.generate_content.side_effect = [TimeoutError(), SimpleNamespace(text=' ')]
    monkeypatch.setattr(gemini, '_get_client', lambda: client)
    with pytest.raises(gemini.GeminiUnavailable):
        gemini.generate('고민', models=['first','second'])


@pytest.mark.parametrize('values', [[], [0.1]*767, None])
def test_embedding_wrong_dimension(monkeypatch, values):
    client = Mock()
    client.models.embed_content.return_value = SimpleNamespace(embeddings=[SimpleNamespace(values=values)])
    monkeypatch.setattr(emb, '_get_client', lambda: client)
    with pytest.raises(RuntimeError):
        emb.create_embedding('고민')


def test_embedding_document(monkeypatch):
    client = Mock()
    vector = [0.1]*768
    client.models.embed_content.return_value = SimpleNamespace(embeddings=[SimpleNamespace(values=vector)])
    monkeypatch.setattr(emb, '_get_client', lambda: client)
    assert emb.create_embedding(' 고민 ') == vector
    args = client.models.embed_content.call_args.kwargs
    assert args['contents'] == '고민'
    assert args['config'].task_type == 'RETRIEVAL_DOCUMENT'
    assert args['config'].output_dimensionality == 768


def test_embedding_blank(monkeypatch):
    client = Mock()
    monkeypatch.setattr(emb, '_get_client', client)
    with pytest.raises(ValueError):
        emb.create_embedding(' ')
    client.assert_not_called()


def test_embedding_empty_response(monkeypatch):
    client = Mock()
    client.models.embed_content.return_value = SimpleNamespace(embeddings=[])
    monkeypatch.setattr(emb, '_get_client', lambda: client)
    with pytest.raises(RuntimeError):
        emb.create_embedding('고민')


def test_query_embedding(monkeypatch):
    from google import genai
    client = Mock()
    vector = [0.2]*768
    client.models.embed_content.return_value = SimpleNamespace(embeddings=[SimpleNamespace(values=vector)])
    monkeypatch.setenv('GEMINI_API_KEY','fake-test-key')
    monkeypatch.setattr(genai,'Client',lambda **kwargs: client)
    assert emb.create_query_embedding('검색') == vector
    assert client.models.embed_content.call_args.kwargs['config'].task_type == 'RETRIEVAL_QUERY'
