from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)
TOKEN = "test-token"


def setup_function():
    import os
    os.environ["LARA_AUTOMATION_TOKEN"] = TOKEN


def test_task_list_requires_token():
    response = client.get("/v1/automation/tasks")
    assert response.status_code == 401


def test_task_list_returns_only_registered_tasks():
    response = client.get(
        "/v1/automation/tasks",
        headers={"Authorization": f"Bearer {TOKEN}"},
    )
    assert response.status_code == 200
    task_ids = {task["id"] for task in response.json()["tasks"]}
    assert task_ids == {"python.runtime_info", "ollama.list_models"}


def test_read_only_task_runs_without_approval_flag():
    # python.runtime_info is registered with approval_required=False; the old
    # client-side "approved" flag no longer has any effect.
    response = client.post(
        "/v1/automation/run",
        headers={"Authorization": f"Bearer {TOKEN}"},
        json={"task_id": "python.runtime_info"},
    )
    assert response.status_code == 200


def test_registered_python_runtime_task_runs():
    response = client.post(
        "/v1/automation/run",
        headers={"Authorization": f"Bearer {TOKEN}"},
        json={"task_id": "python.runtime_info", "approved": True},
    )
    assert response.status_code == 200
    assert response.json()["status"] == "completed"
    assert response.json()["result"]["python_version"]


def test_arbitrary_python_execution_is_not_available():
    response = client.post(
        "/v1/automation/run",
        headers={"Authorization": f"Bearer {TOKEN}"},
        json={"task_id": "python.exec", "approved": True},
    )
    assert response.status_code == 404
