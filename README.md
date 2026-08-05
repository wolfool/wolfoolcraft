# wolfoolcraft — 커스텀 제작대

시간이 걸리는 제작 대기열을 가진 커스텀 제작대 플러그인. 제작대를 설치하고,
레시피를 걸어 두면 시간이 지나면서 완성된다.

- **Minecraft** 1.21.x
- **서버** Paper
- **Java** 21
- **필수 의존** [CraftEngine](https://github.com/Xiao-MoMi/craft-engine) — 커스텀 가구/아이템
- **선택 의존** AuraSkills — 숙련도로 레시피 해금

---

## 조작

| 동작 | 조작 |
|---|---|
| 설치 모드 | 제작대를 들고 **F**(다른 손과 아이템 맞바꾸기) |
| 회전 | 설치 모드에서 **좌클릭** (웅크리고 좌클릭 = 반대 방향) |
| 설치 | 설치 모드에서 **우클릭** |
| 모드 종료 | **F** 한 번 더 |
| 회수 | 설치된 제작대를 **웅크리고 우클릭** |
| GUI 열기 | 설치된 제작대를 **우클릭** |

설치 모드에서는 미리보기가 시선을 따라다닌다. 놓을 수 있으면 초록, 못 놓으면 빨강으로
빛나고 하단에 조작 안내가 뜬다. 미리보기는 **본인에게만** 보인다.

아이템이 실제로 소모되는 시점은 **설치가 성공한 순간**이다. 자리를 못 찾거나 개수 제한에
걸리면 아무것도 잃지 않는다.

**땅에 대고 우클릭해도 놓이지 않는다.** 설치는 F 키로 들어가는 설치 모드에서만 된다.
그래서 CraftEngine 쪽 아이템에 `furniture_item` 동작을 일부러 안 붙였다 — 붙이면
우클릭 한 번에 바로 놓여서 미리보기로 자리와 방향을 잡는 과정을 건너뛰게 된다.

> **설치된 제작대와의 상호작용은 전부 CraftEngine 의 `FurnitureInteractEvent` 로 받는다.**
> 히트박스가 블록이 아니라 엔티티라서 `PlayerInteractEvent` 가 아예 안 온다. 회수(웅크리고
> 우클릭)를 거기에 걸어두면 영영 안 불린다.

---

## 제작대 GUI

54칸이고 배치는 이렇다.

| 구역 | 슬롯 | 내용 |
|---|---|---|
| 레시피 격자 | 1~5, 10~14, 19~23, 28~32, 37~41 | 5×5. 재료를 청사진처럼 보여준다 |
| 결과물 | 25 | 클릭하면 제작 시작 |
| 도감 | 7 | 보유 레시피 목록 |
| 대기열 | 45~53 | 기본 3칸, 나머지 6칸은 열쇠로 연다 |

대기열은 아이템마다 제작 시간이 있고, 다 되면 클릭해서 회수한다. 0.5초마다 갱신되며
접속을 끊어도 이어진다.

### 만드는 방법이 두 가지다

**1. 격자에 직접 올리기** — 도감을 거치지 않고 그냥 연 상태(`/wb gui`, 제작대 우클릭)에서는
5×5 격자가 **입력칸**이다. 재료를 올려놓으면 맞는 레시피를 찾아 결과 칸에 띄우고, 누르면
**격자에 올린 것만** 소모해서 대기열에 넣는다.

- 정형 레시피는 모양만 맞으면 격자 어디에 놓아도 된다
- Shift 클릭하면 격자 빈칸으로만 들어간다 (결과 칸이나 대기열로 새지 않게 직접 옮긴다)
- 창을 닫으면 격자에 남은 건 **가방으로 돌려준다**. 가방이 꽉 찼으면 발밑에 떨군다

**2. 도감에서 고르기** — 도감에서 레시피를 누르면 격자가 **청사진**으로 채워지고 손댈 수
없게 된다. 이때는 격자가 아니라 **가방을 훑어서** 재료를 가져간다. 청사진 아이콘에
필요량과 보유량이 같이 뜬다.

### 열쇠

잠긴 대기열 칸을 클릭하면 **열쇠**(`wolfoolcraft:queue_key`) 하나를 소모하고 열린다.
없으면 안내만 뜨고 아무 일도 일어나지 않는다. 상한은 9칸.

열쇠는 `/wb key` 로 바로 받거나, 도감 **도구함** 탭의 레시피(철괴 3 + 레드스톤 1)로 만든다.

### 숙련도

레시피에 `skill-type` 과 `required-proficiency` 를 적으면 AuraSkills 의 그 스킬 레벨이
기준치를 넘어야 제작할 수 있다. 제작을 시작하면 `xp-reward` 만큼 XP 를 준다.

AuraSkills 가 없으면 레벨을 0 으로 보므로, 요구 숙련도가 없는 레시피만 쓸 수 있다.
플러그인 자체는 정상 동작한다.

> XP 는 완성이 아니라 **제작을 시작할 때** 준다. 대기열은 서버 재시작을 넘어 살아남는데
> 세션이 레시피 id 를 안 들고 있어서, 수령 시점에는 어떤 스킬이었는지 알 방법이 없다.

---

## 도감

책 모양 54칸 창이다. 오른쪽 띠에 탭 5개가 있고, 레시피는 왼쪽 책 칸(0~6열 × 5줄)에
놓인다. 한 장에 35개까지 들어가고 넘치면 책 아래 양쪽 끝 화살표(45 / 53번)로 넘긴다.
넘길 데가 없는 쪽에는 화살표를 안 놓는다.

탭을 누르는 자리는 **7열**(7, 16, 25, 34, 43)이다. 그림에서 탭 아이콘이 GUI 좌표
x 132~155 에 있어서 7열에 걸치기 때문이다. 8열은 띠의 오른쪽 꼬리라 막아만 둔다.

**눌린 탭은 배경 그림이 통째로 갈리면서 표시된다.** 배경이 `tab1_selected.png` 처럼
'그 탭이 눌린 상태' 그림으로 와 있어서, 선택된 탭에 맞는 그림을 깔면 그만이다.
그래서 탭 칸에는 색깔 배너 대신 **투명한 아이템**만 놓는다 — 눌리기만 하면 되기 때문이다.

탭은 `config.yml` 의 `gui.encyclopedia.tabs` 에서 정한다. 목록 순서가 그림의 탭
순서(위 → 아래)다.

| 키 | 뜻 |
|---|---|
| `name` | 마우스를 올렸을 때 뜨는 이름 |
| `char` | 이 탭이 눌렸을 때 깔 배경 글리프. CraftEngine `gui.yml` 의 `char` 와 짝 |
| `mode` | `all` = 전부 / `list` = `categories` 에 적은 것만 / `rest` = 나머지 전부 |
| `categories` | `mode: list` 일 때 묶을 레시피 `category` |

기본값은 **농축 / 도구·무기 / 갑옷 / 가구 / 도구함(나머지)** 이다.
`rest` 탭을 하나 두면 어디에도 안 묶인 새 카테고리가 사라지지 않고 거기로 모인다.

---

## CraftEngine 자산

플러그인은 아이템과 가구를 직접 만들지 않는다. CraftEngine 에 정의된 걸 ID 로 가져다 쓴다.

| ID | 용도 |
|---|---|
| `wolfoolcraft:workbench` | 제작대 — 아이템과 가구를 **따로** 정의한다 (위 참고) |
| `wolfoolcraft:queue_key` | 대기열 열쇠 |
| `wolfoolcraft:empty` | GUI 빈칸을 막는 완전 투명 아이템 |
| `wolfoolcraft:workbench_bg` | 제작대 GUI 배경 그림 |
| `wolfoolcraft:encyclopedia_tab1`~`tab5` | 도감 GUI 배경 그림 (탭마다 한 장) |

### 크기·높이는 세 곳이 같아야 한다

| 어디 | 값 |
|---|---|
| 가구 yml `scale` | `0.75` |
| 가구 yml `translation.y` | `0.375` (scale 의 절반) |
| 플러그인 `config.yml` `workbench.preview-scale` / `preview-y-offset` | `0.75` / `0.375` |

디스플레이 엔티티는 모델을 **엔티티 위치를 중심으로** 그린다. 그래서 바닥에 세우려면
크기의 절반만큼 올려야 한다. 값이 어긋나면 설치 미리보기와 실제로 놓인 물건의 크기나
높이가 다르게 보인다.

모델 json 의 `display.fixed.scale` 은 **1 로 둬야** 위 계산이 그대로 맞는다. 여기에
0.5 같은 값이 들어가면 실제 크기가 그만큼 줄어든다.

> ⚠ `config.yml` 은 **서버의 `plugins/wolfoolcraft/config.yml`** 이다. jar 를 새로
> 올려도 이미 있는 그 파일은 안 덮어써진다. 기본값을 바꿨으면 서버 쪽도 같이 고쳐야 한다.

히트박스는 기준점이 다르다. element 의 `translation` 은 모델 한가운데를 옮기지만
interaction 히트박스의 `position` 은 상자 **바닥**이다. 여기에 translation 값을 그대로
쓰면 클릭 판정만 위로 떠서, 제작대를 눌러도 GUI 가 안 열린다.

### GUI 배경

인벤토리 배경을 바꾸는 방법은 따로 없어서, **창 제목에 그림 한 글자를 넣고 왼쪽 위로
밀어** 화면을 덮는다. CraftEngine 기본 GUI 와 같은 방식이다.

- 그림 등록: `configuration/gui.yml` (`height`, `ascent`, `char`)
- 미는 양: 플러그인 `config.yml` 의 `gui.*.offset-x`
- 미는 데 쓰는 빈칸 폰트: `assets/wolfoolcraft/font/space.json` (1·2·4·…·128px)

위치가 안 맞으면 **위아래는 `ascent`, 좌우는 `offset-x`** 를 조절한다.
그림은 원본 픽셀 1개 = GUI 1칸이라 폭 182px 로 맞춰 뒀다.

서버에 넣을 위치는 `plugins/CraftEngine/resources/wolfoolcraft/` 다.
모델과 텍스처는 [`models/make_workbench.py`](models/make_workbench.py) 로 다시 만들 수 있다.

```bash
cd models && python make_workbench.py   # out/ 에 workbench.json + workbench.png
```

자산을 바꾸면 서버에서 `/ce reload` 를 해야 리소스팩이 다시 구워진다.

---

## 명령어

| 명령어 | 설명 |
|---|---|
| `/wb gui` | 제작대 GUI 를 연다 |
| `/wb item` | 제작대를 지급한다 |
| `/wb key` | 대기열 열쇠를 지급한다 |
| `/wb xp` | 숙련도 레벨·XP 와 제작 횟수를 본다 |
| `/wb lock [칸수]` | 대기열을 다시 잠근다. 숫자를 빼면 기본값(3칸)으로 |
| `/wb admin` | 관리자용 도감 |
| `/wb reload` | config.yml / recipes.yml 다시 읽기 |

`/wb lock` 은 열쇠로 칸이 열리는지 확인하려고 되돌릴 때 쓴다. 잠근 칸에 제작 중인 게
남아 있으면 가려지기만 하고 사라지지는 않는다 — 칸을 다시 늘리면 그대로 보인다.

`/wb xp` 는 레시피에 쓰인 스킬별로 레벨·XP·레시피 수·잠긴 개수·제작 횟수를 보여준다.
AuraSkills 가 없으면 그 사실을 먼저 알려준다. XP 를 줄 곳이 없어서 제작해도 안 오르고
요구 숙련도가 있는 레시피는 계속 잠긴 채로 있기 때문이다. 레시피의 `skill-type` 이
AuraSkills 에 없는 이름이면 그것도 짚어준다 — 그 경우 XP 가 조용히 버려진다.

---

## 설정

`config.yml`

```yaml
workbench:
  item-id: "wolfoolcraft:workbench"
  furniture-id: "wolfoolcraft:workbench"
  queue-key-id: "wolfoolcraft:queue_key"

crafting:
  max-workbenches-per-player: 5
  default-queue-slots: 3
  max-queue-slots: 9
```

`recipes.yml` — 레시피 정의. `category`, `skill-type`, `required-proficiency`,
`xp-reward`, `crafting-time`, `shape`/`keys` 또는 `ingredients`, `result`.

### 재료에 CraftEngine 아이템 쓰기

`material` 대신 **`item`** 에 CraftEngine ID 를 적으면 커스텀 아이템이 된다.
결과물·`ingredients`·`keys` 어디서나 된다.

```yaml
wood_bundle:
  category: "농축"
  result:
    item: "wood1:woods1"      # 원목 묶음 1
    amount: 1
  ingredients:
    - item: "wood1:wood"      # 원목
      amount: 50
  crafting-time: 60
```

> 커스텀 아이템은 대부분 종이 같은 흔한 재료를 바탕으로 만들어진다. 그래서
> **`material: PAPER` 로 적은 자리에는 커스텀 아이템이 안 들어간다.** 안 그러면
> 종이 기반 커스텀 아이템 50개가 '종이 50개' 로 잡혀 엉뚱하게 제작돼 버린다.

여기 적은 ID 가 실제로 있는지는 **CraftEngine 이 자기 설정을 다 읽은 뒤** 자동으로
검사해서 로그에 남긴다. 오타가 있으면 재료가 그냥 종이로 보일 뿐 이유를 알 수 없어서다.
검사 시점을 `onEnable` 이나 `ServerLoadEvent` 로 잡으면 안 된다 — CraftEngine 은
그보다 늦게 팩을 읽어서 멀쩡한 ID 도 '없다' 고 나온다. `CraftEngineReloadEvent` 를 쓴다.

### 저장

`plugins/wolfoolcraft/database.db` (SQLite). 해금 슬롯 수, 설치한 제작대 수,
제작 횟수, 대기열, 발견한 레시피를 보관한다.

제작 횟수는 **완성이 아니라 '제작을 시작한' 시점**에 센다. 숙련도 XP 를 주는 시점과
같아야 `/wb xp` 에서 본 숫자끼리 앞뒤가 맞는다.

---

## 빌드

```bash
./gradlew build
```

산출물은 `build/libs/`.

---

## 남은 것

- **요리대 / 정제기** — 문서에는 있지만 아직 제작대만 구현했다. 제한 개수는 각 5개 / 10개.
- **업그레이드 시 인챈트 미이전** 규칙.
- 레시피 재료로 **CraftEngine 아이템** 쓰기 (`RecipeManager` 는 아직 바닐라 `Material` 만 본다).
- 관리자 도감의 레시피 활성화/비활성화.
- 패키지가 `com.myserver.workbench` 로 남아 있다. 저장소 이름과 안 맞는다.
