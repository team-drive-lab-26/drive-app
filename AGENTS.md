# 도란

가족 여행 목소리 앨범. Pleos Connect 차량용 앱 공모전 출품작.

- `app/`: Android 앱. Kotlin, Compose, 단일 Activity, Navigation Compose. Android Studio에서 이 폴더를 연다.
- `server/`: API 서버
- `ai/`: AI 에이전트, RAG
- `docs/`: 기준 자료

앱 명령: `cd app && ./gradlew assembleDebug`

앱 코드는 `com.doran.app` 아래 한 모듈에서 나눈다. 화면은 `feature/<이름>`, 공통 코드는 `core/`(`model`, `data`, `designsystem`), 화면 경로는 `navigation/`에 둔다. 새 영역은 필요해질 때 추가한다(예: `core/voice`). `feature`끼리는 서로 참조하지 않는다.

## 작업 전

[docs/index.md](docs/index.md)에서 해당 영역을 찾아 먼저 읽는다. 기획은 자주 바뀌므로 `docs/planning/decisions.md`를 우선한다.

맡은 폴더 밖을 고쳐야 하면 먼저 사람에게 알린다.

## 문서

- API, DB, AI 입출력을 바꾸면 같은 푸시에서 `docs/api`, `docs/db`, `docs/ai`도 고친다.
- 기획 방향이 바뀌면 `docs/planning/decisions.md`에 한 줄 추가한다.
- 화면, 문구, 내부 로직 변경은 문서를 고치지 않아도 된다.
- 문서와 코드가 다르면 임의로 고르지 말고 사람에게 묻는다.

## 제품 규칙

- 주행 중에는 음성, 작은 정지 아이콘, 자막 한 줄만 쓴다. 지도, 카드, 목록, 애니메이션은 넣지 않는다.

## 브랜치

- `main`: 점검일에만 `develop`를 합친다. 항상 빌드되어야 한다.
- `develop`: 작업을 합치는 곳. 리뷰와 PR 없이 머지해 푸시한다.
- 1인 브랜치: 각자 작업하는 곳.
- 머지 전에 최신 `develop`를 받고, 앱을 건드렸으면 빌드를 확인한다.
- 머지 후 카톡에 공유할 한 줄 요약을 작성해 사람에게 넘긴다. 문서를 바꿨으면 경로를 함께 적는다.

## 보안과 제출

- `local.properties`, `.env`, 키 파일, API 키를 커밋하거나 출력하지 않는다.
- 제출은 익명이다. 코드, 문서, 커밋 메시지에 팀원 이름, 연락처, 소속을 쓰지 않는다.
- 커밋 메시지에 `Co-Authored-By` 등 AI 도구 표시를 넣지 않는다.
- 개인 AI 도구 설정(`CLAUDE.local.md`, `.claude/settings.local.json`)은 커밋하지 않는다. 팀이 함께 쓸 스킬과 설정은 올려도 된다.
- `.gitignore`는 폴더별로 담당자가 관리한다. 최상위에는 공통 항목만 둔다.

## 주석

이유가 필요한 곳에만 짧게 한국어로 쓴다. 코드를 반복하는 주석은 쓰지 않는다.
