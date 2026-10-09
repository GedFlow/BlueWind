# 기능 명세: BlueWind (윈도우 리모컨)

## 대상 환경 (확인된 사실)

- 폰: Galaxy S24. 블루투스 HID Device 기능 동작 확인됨
  (시중 앱으로 PC 마우스·키보드 제어 성공, 두 손가락 스크롤도 무난하게 동작)
- PC: Windows, 한국어 입력기 사용. **오른쪽 Alt = 한/영** (사용자의 실물 키보드와 같은 설정)
- 연결: Bluetooth Classic HID (`BluetoothHidDevice` API). PC에 설치하는 프로그램 없음.

## 화면 구조 (가로 고정)

- **메인 화면**: 트랙패드가 화면 전체 (v0.2 사용자 요청으로 변경)
  - 좌상단: 키보드 버튼 (항상 떠 있음)
  - 우상단: 연결 상태 버튼. 누르면 드롭다운: 아래로 볼륨 +, 볼륨 −, 음소거 / 왼쪽으로 연결 설정, 프레젠테이션
- **키보드 패널**: 키보드 버튼을 누르면 화면 대부분을 덮는다. 닫기 버튼(키보드 버튼과 같은 자리)으로 메인 화면 복귀.
- **프레젠테이션 모드**: 슬라이드 제어 버튼 + 레이저 포인터 이동용 트랙패드 영역
- **연결 화면**: 페어링된 PC 목록에서 선택해 연결. 최초 페어링 안내 포함.
- 세부 크기와 배치는 실기기 피드백으로 조정한다.

## 1. 마우스 (트랙패드)

노트북 트랙패드와 같은 조작감이 목표.

| 제스처 | 동작 |
|---|---|
| 한 손가락 이동 | 커서 이동 (상대 좌표) |
| 한 손가락 탭 | 왼쪽 클릭 |
| 두 손가락 탭 | 오른쪽 클릭 |
| 탭 직후 다시 눌러서 이동 | 왼쪽 버튼 누른 채 드래그 |
| 두 손가락 상하 드래그 | 세로 스크롤 (Wheel) |
| 두 손가락 좌우 드래그 | 가로 스크롤 (AC Pan) |

- 커서 감도, 스크롤 감도, 스크롤 방향 반전은 설정값으로 둔다. 초기값은 실기기에서 튜닝한다.
- 터치 이동량은 누적해서 일정 간격으로 묶어 보낸다(리포트 폭주 방지). 간격은 실측으로 정한다.
- 사이드 스크롤 영역은 만들지 않는다 (두 손가락 스크롤로 대체).

## 2. 키보드

- 앱 자체 키보드 패널. Android IME는 쓰지 않는다.
- 기본 레이어 (노트북 키보드 배열):

| 열 | 키 |
|---|---|
| 1 | Esc ` 1 2 3 4 5 6 7 8 9 0 - = Backspace |
| 2 | Tab Q W E R T Y U I O P [ ] \ |
| 3 | CapsLock A S D F G H J K L ; ' Enter |
| 4 | Shift Z X C V B N M , . / Shift |
| 5 | Fn Ctrl Win Alt Space 한/영 한자 ← ↑ ↓ → |

- 한/영 = 오른쪽 Alt, 한자 = 오른쪽 Ctrl 로 전송한다.
- Fn은 PC로 보내지 않는 앱 내부 레이어 전환 키. Fn 레이어: F1~F12, Insert, Delete, Home, End, PageUp, PageDown, PrintScreen
- 키 라벨은 영문과 두벌식 한글을 같이 표시한다 (예: `R / ㄱ`).
  - (v0.4 사용자 요청) 표시 언어를 크게, 다른 쪽을 작게 표시한다. 앱은 PC의 한/영 상태를 직접 알 수 없으므로
    앱의 한/영 키를 누를 때마다 표시를 바꾸는 추정값이다. 어긋나면 상단 "표시" 버튼으로 맞춘다(PC로는 보내지 않음).
  - 알파벳은 CapsLock(PC가 보내는 LED 리포트 기준)과 Shift 상태에 따라 대소문자로 표시한다.
- 동작 방식:
  - 터치 DOWN = 키 누름, 터치 UP = 키 뗌. 실물 키보드와 같다.
  - 멀티터치로 여러 키 동시 누름 지원 (Ctrl+C, Alt+Tab 등).
  - (v0.4 사용자 요청) Shift는 토글: 탭 = 다음 키 한 번만, 두 번 탭 = 고정, 다시 탭 = 해제. 누른 채 다른 키를 눌러도 된다.
    Shift 자체는 바로 보내지 않고 다음 키를 보낼 때 Shift+키로 함께 보낸다. Ctrl·Alt는 실물 키보드 방식 그대로.
  - 키를 누를 때 아주 약한 진동 (v0.2 사용자 요청).
  - 키를 누르고 있을 때의 반복 입력은 PC가 처리하므로 앱에서 반복 전송하지 않는다.
- 한글 검증 문자열: `ㅙ ㅟ ㅞ 싫 닭 았 갂 왜 뷁 앉` → 실물 키보드와 똑같이 입력되어야 한다.

## 3. 프레젠테이션 모드

| 버튼 | 전송 키 |
|---|---|
| 처음부터 시작 | F5 |
| 현재 슬라이드부터 | Shift+F5 |
| 다음 | → |
| 이전 | ← |
| 종료 | Esc |
| 검은 화면 | . (마침표) — B는 Windows 입력기가 한글 상태면 동작하지 않아 v0.4에서 변경 |
| 레이저 포인터 | Ctrl+L |

- 레이저 포인터를 켠 뒤 화면 안의 트랙패드 영역으로 포인터를 움직인다.
- 다음/이전 버튼은 크게 만든다.
- 구조적 한계 (구현 시도하지 않음): HID는 PC로 보내기만 하는 단방향이라 슬라이드 번호, 미리보기, 발표자 노트를 폰에 표시할 수 없다.
- 키 입력은 활성 창으로 가므로 PowerPoint 창이 앞에 있어야 한다.
- 레이저 포인터 영역은 커서 이동만 한다 (탭·스크롤이 슬라이드를 넘기지 않도록).
- 단축키는 한꺼번에 몰아 보내지 않고 사람이 누르는 것처럼 간격을 두고 보낸다.

## 연결 유지

- Android 블루투스 HID 서비스는 앱이 화면에서 벗어나면(홈, 앱 전환, 화면 꺼짐) 등록을 강제로 해제한다
  (AOSP HidDeviceService: 앱 중요도가 IMPORTANCE_VISIBLE보다 낮아지면 unregister).
- 그래서 Foreground Service(`connectedDevice`, 상단 알림)로 연결을 유지한다 (v0.3).
- 종료: 알림의 "종료" 버튼 또는 최근 앱 목록에서 앱을 지운다.

## 4. 볼륨

- 앱 내 버튼: 볼륨 +, 볼륨 −, 음소거 (HID Consumer Control)
- 버튼을 누르고 있으면 앱이 일정 간격으로 "누름 → 뗌"을 반복 전송한다.
- 폰 물리 볼륨키는 사용하지 않는다 (폰 자체 볼륨 조절 그대로).

## HID 기술 명세

### API

- `BluetoothAdapter.getProfileProxy(context, listener, BluetoothProfile.HID_DEVICE)`로 `BluetoothHidDevice` 획득
- `registerApp(sdp, inQos, outQos, executor, callback)`으로 등록. 등록 전에는 `connect()`가 실패한다.
- `BluetoothHidDeviceAppSdpSettings`의 subclass: `BluetoothHidDevice.SUBCLASS1_COMBO`
- 연결 상태는 `Callback.onConnectionStateChanged`로 추적한다.
- 리포트 전송: `sendReport(device, reportId, data)`
- 앱 종료 시 `unregisterApp()`

### Report Descriptor (하나의 descriptor에 Report ID 3개)

| Report ID | 용도 | 페이로드 |
|---|---|---|
| 1 | 키보드 | 8바이트: modifier, reserved, key1~key6 (LED output report는 선택) |
| 2 | 마우스 | buttons(3비트 + 패딩 5비트), X int8, Y int8, Wheel int8, AC Pan int8 |
| 3 | Consumer | 16비트 usage 1개 (누름: usage 값, 뗌: 0x0000) |

### 주요 Usage 값

- Keyboard (Usage Page 0x07)
  - A=0x04 … Z=0x1D, 1=0x1E … 9=0x26, 0=0x27
  - Enter 0x28, Esc 0x29, Backspace 0x2A, Tab 0x2B, Space 0x2C
  - F1=0x3A … F12=0x45
  - Right 0x4F, Left 0x50, Down 0x51, Up 0x52
- Modifier 비트: LCtrl 0x01, LShift 0x02, LAlt 0x04, LGUI(Win) 0x08, RCtrl 0x10, RShift 0x20, RAlt 0x40, RGUI 0x80
  - 한/영 = RAlt(0x40), 한자 = RCtrl(0x10)
- Consumer (Usage Page 0x0C): Volume Up 0xE9, Volume Down 0xEA, Mute 0xE2
- Mouse: Wheel = Generic Desktop 0x38, 가로 스크롤 = Consumer Page AC Pan 0x0238

### 연결 흐름

1. 앱 시작 → 권한 요청 → 프록시 획득 → `registerApp`
2. 최초 1회: 폰을 검색 가능 상태로 → Windows "블루투스 또는 기타 장치 추가"에서 폰 선택 → 페어링
3. `onConnectionStateChanged`로 연결 확인 후 화면에 상태 표시
4. 이후: 연결 화면에서 페어링된 PC를 골라 `connect(device)`로 연결

## 개발 단계

각 단계 끝: 빌드 → `installDebug` → 사용자에게 테스트 항목 제시 → 결과 확인 후 다음 단계.

| 단계 | 내용 | 사용자 검증 항목 |
|---|---|---|
| Phase 0 | 환경 점검 (CLAUDE.md 참고) | `adb devices`에 S24 표시, 빈 앱 빌드·설치 성공 |
| Phase 1 | 권한, HID 등록, 페어링·연결, 연결 상태 표시 | Windows 블루투스 목록에 장치 표시, "연결됨" |
| Phase 2 | 볼륨 버튼 (가장 단순한 리포트로 전체 경로 확인) | PC 볼륨 표시가 뜨고 조절·음소거 동작 |
| Phase 3 | 트랙패드 | 이동, 클릭, 우클릭, 드래그, 세로·가로 스크롤 |
| Phase 4 | 키보드 패널 | 영문, 숫자, 특수문자, 한글 검증 문자열, 한/영 전환, Ctrl+C/V, Alt+Tab, Win, Backspace 길게 누르기 |
| Phase 5 | 프레젠테이션 모드 | PowerPoint 슬라이드쇼에서 버튼별 동작, 레이저 포인터 이동 |
| Phase 6 | 감도·배치 튜닝 | 사용자 피드백 반영 |

## 제안 사항 (사용자 확인 후에만 구현)

- ~~키 누를 때 짧은 진동 피드백~~ → v0.2에서 구현
- 앱 사용 중 화면 꺼짐 방지 (발표 중 유용)
- 앱 시작 시 마지막으로 연결한 PC에 자동 재연결
