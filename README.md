# wolfoolcraft — 커스텀 작업대

시간이 걸리는 제작 대기열을 가진 커스텀 작업대 플러그인. 작업대를 설치하고,
레시피를 걸어 두면 시간이 지나면서 완성된다.

- **Minecraft** 1.21.x
- **서버** Paper
- **Java** 21
- **필수 의존** [CraftEngine](https://github.com/Xiao-MoMi/craft-engine) — 커스텀 가구/아이템
- **선택 의존** AuraSkills — 숙련도 연동

---

## ⚠️ 현재 상태: 미완성

**설치 기능이 아직 동작하지 않습니다.**

`InstallationManager.confirmInstallation()` 에서 CraftEngine API 호출이 TODO로 남아
있습니다. 현재 동작은 이렇습니다.

```java
// TODO: Call CraftEngine API to place the furniture at 'loc' with 'yaw'
// CraftEngineAPI.placeFurniture("myserver:crafting_workbench", loc, yaw);

player.sendMessage("§a[작업대] 설치가 완료되었습니다.");   // 완료라고 안내하고
player.getInventory().removeItem(session.getItem().asOne()); // 아이템은 소모하지만
```

→ **"설치 완료" 메시지가 뜨고 아이템이 사라지지만 실제로 설치되는 것은 없습니다.**
실서버에 올리기 전에 반드시 해결해야 합니다.

남은 TODO:

| 위치 | 내용 |
|---|---|
| `InstallationManager.java:78` | CraftEngine 가구 배치 API 호출 (**핵심**) |
| `InstallationListener.java:24` | 손에 든 아이템이 커스텀 작업대인지 판별 |
| `RecipeManager.java:43` | 레시피 재료에 CraftEngine 아이템 지원 |
| `AdminEncyclopediaGUI.java:26` | 레시피 활성화/비활성화 |

---

## 기능

### 설치 모드
작업대 아이템을 들면 설치 모드에 들어간다. 미리보기가 표시되고, **웅크린 채
우클릭**하면 확정된다. 설치 중에는 바닐라 블록 설치와 상호작용이 막힌다.

### 제작 대기열
레시피를 걸면 즉시 완성되지 않고 대기열에 쌓여 시간이 지나며 완성된다. 대기열은
0.5초마다 갱신되고(`QueueUpdateTask`), 접속 종료 시 저장되어 다시 접속하면
이어진다.

### 도감
- `EncyclopediaGUI` — 플레이어용 레시피 도감
- `AdminEncyclopediaGUI` — 관리자용

### 저장
`plugins/wolfoolcraft/database.db` (SQLite). 플레이어별 해금 슬롯 수, 설치한 작업대
수, 제작 횟수, 대기열을 보관한다.

---

## 명령어

| 명령어 | 설명 |
|---|---|
| `/wb` | 작업대 관련 명령어 (탭 완성 지원) |

---

## 설정

| 파일 | 내용 |
|---|---|
| `config.yml` | 플러그인 설정 |
| `recipes.yml` | 레시피 정의 |

---

## 빌드

```bash
./gradlew build
```

산출물은 `build/libs/`. CraftEngine 과 AuraSkills API 는
`https://repo.momirealms.net/releases/` 에서 받아온다.

---

## 알려진 개선 필요 사항

- 패키지가 `com.myserver.workbench` — 플레이스홀더 그대로다. 다른 프로젝트와 맞춰
  `kr.wolfool.*` 로 옮기는 편이 낫다.
- `plugin.yml` 의 `author` 가 실제 작성자와 다르다.
