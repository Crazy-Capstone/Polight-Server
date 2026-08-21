package polight.server.domain.terms.entity;

/** 약관 조항이 보장 규칙의 <b>무엇</b>을 뒷받침하는지. 같은 조항이 역할을 달리해 여러 번 연결될 수 있다. */
public enum TermsCoverageSourceRole {
  PRIMARY,
  CONDITION,
  EXCLUSION,
  LIMIT,
  PROCEDURE,
  REQUIRED_DOCUMENT,
  DEFINITION
}
