# 도란

가족 여행 목소리 앨범. 차가 멈춘 곳과 그곳에서 남긴 가족의 목소리를 차량 데이터로 기록하는 차량용 앱입니다.

## 구조

| 폴더 | 내용 |
| --- | --- |
| `app/` | Android 앱 |
| `server/` | API 서버 |
| `ai/` | AI 에이전트, RAG |
| `docs/` | 기준 자료. [docs/index.md](docs/index.md)부터 읽으세요 |

## 앱 실행

Android Studio에서 `app/` 폴더를 열고 `app` 구성을 실행합니다. 터미널에서는 다음과 같습니다.

```bash
cd app
./gradlew assembleDebug
```

JDK 17 또는 21로 실행합니다. Gradle 8.13은 JDK 25에서 실행되지 않으므로, Android Studio에서는 Settings > Build Tools > Gradle의 Gradle JDK를 21로 맞춥니다.

디버그 빌드 하단의 버튼으로 기어 값을 바꿔 주행·정차 화면 전환을 확인할 수 있습니다.

## 작업 방식

AI 도구는 [AGENTS.md](AGENTS.md)를 따릅니다. 사람도 같은 규칙을 씁니다.
