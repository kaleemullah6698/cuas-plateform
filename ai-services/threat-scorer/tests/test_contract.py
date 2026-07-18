"""
Shared AI service conformance suite (ARCHITECTURE.md section 6.7).

The identical file runs against every AI service and against the mock provider.
Passing it is what makes a service integrable without negotiation.

Contract rules under test (section 6.2):
  1. model_version and model_sha are always populated.
  2. Confidence is calibrated and within [0, 1].
  3. A service never returns an empty response; absence of detection is
     label "background" with a confidence, not silence.
  4. The service honours the orchestrator-supplied timeout budget.
  5. The service writes to no database and no Kafka topic.

Scaffold only. Test bodies are unimplemented.
"""

import pytest


@pytest.mark.skip(reason="scaffold — not yet implemented")
def test_response_always_carries_model_version_and_sha():
    raise NotImplementedError


@pytest.mark.skip(reason="scaffold — not yet implemented")
def test_confidence_is_calibrated_and_in_unit_range():
    raise NotImplementedError


@pytest.mark.skip(reason="scaffold — not yet implemented")
def test_absence_of_detection_returns_background_label():
    raise NotImplementedError


@pytest.mark.skip(reason="scaffold — not yet implemented")
def test_service_honours_timeout_budget():
    raise NotImplementedError
