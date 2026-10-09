# BlueWind (윈도우 리모컨)

Galaxy S24를 Bluetooth HID 장치(키보드 + 마우스 + 미디어키)로 동작시켜 Windows PC를 제어하는 Android 앱.
PC 쪽에는 아무것도 설치하지 않는다. Windows는 이 폰을 일반 블루투스 키보드·마우스로 인식한다.

기능 명세, HID 기술 명세, 개발 단계는 아래 파일에 있다. 작업 전에 반드시 따른다.
@docs/SPEC.md

## 사용자와 소통

- 한국어로 답한다. 고유명사와 코드 용어는 영어 그대로 쓴다.
- 확실한 것과 추정을 구분해서 말한다. 모르거나 확인하지 못한 것은 그렇다고 말한다.
- 단계가 끝날 때마다 사용자가 실기기에서 확인할 항목을 구체적으로 제시하고, 결과를 받은 뒤 다음 단계로 간다.

## 개발 환경 (사용자 PC: Windows)

- Android Studio로 생성한 프로젝트: Empty Activity, Kotlin, Jetpack Compose, minSdk 28 (Android 9)
- JDK: Android Studio 내장 JBR. 터미널에서 빌드하려면 JAVA_HOME이 필요하다.
  - 기본 경로 예: `C:\Program Files\Android\Android Studio\jbr`
- adb 경로 예: `%LOCALAPPDATA%\Android\Sdk\platform-tools`
- 빌드: `gradlew.bat assembleDebug`
- 설치: `gradlew.bat installDebug` (S24가 USB 디버깅으로 연결된 상태)
- 로그: `adb logcat -s WinRemote` (앱 로그 태그는 `WinRemote`로 통일)
- 시스템 환경 변수를 영구 변경해야 하면 직접 바꾸지 말고 사용자에게 방법을 안내하고 확인을 받는다.

## 반드시 지킬 것

- 에뮬레이터로는 블루투스 HID를 테스트할 수 없다. 동작 검증은 S24 실기기 + Windows PC에서 사용자가 한다.
- Claude Code는 PC 화면의 반응(커서 이동, 입력 결과)을 볼 수 없다. 빌드 성공은 동작 성공이 아니다.
- 키보드는 Android IME(폰 키보드)를 쓰지 않는다. 앱이 직접 그린 키보드 패널에서 HID 키코드를 그대로 보낸다.
  - InputConnection, 텍스트 → 키 변환, 한글 자모 분해 로직을 만들지 않는다.
  - 한글 조합은 Windows 한국어 입력기가 한다. 앱은 실물 키보드처럼 키 누름/뗌만 보낸다.
- 화면 방향은 화면별로 고정한다: 기본은 가로(`android:screenOrientation="landscape"`),
  리모컨 모드·프레젠테이션 모드는 세로 (v0.6 사용자 요청). 센서 기반 회전은 쓰지 않는다.
- 블루투스 HID 등록·연결은 Activity가 아니라 Service(또는 앱 수준 싱글톤)에서 관리한다.
- 명세에 없는 기능은 추가하지 않는다. 필요해 보이면 제안만 하고 사용자 확인 후 구현한다.
  - 특히 추가 금지: PC 서버/클라이언트, Wi-Fi 연결, 폰 물리 볼륨키 가로채기, 자동 화면 회전

## 알려진 함정

- 다른 HID 앱(예: 사용자가 설치한 유료 앱 "Bluetooth Keyboard & Mouse")이 HID 장치로 등록된 상태면
  registerApp이 실패하거나 충돌할 수 있다(확인 필요). 테스트 전에 그 앱을 강제 종료하도록 안내한다.
- Windows는 페어링 시점의 장치 정보를 기억한다. HID descriptor를 바꾼 뒤 동작이 이상하면
  Windows 블루투스 설정에서 장치를 제거하고 다시 페어링한다. 유료 앱으로 페어링했던 기록도 처음에 제거한다.
- Android 12+ 런타임 권한: `BLUETOOTH_CONNECT`(필수). 폰을 검색 가능 상태로 만들 때 `BLUETOOTH_ADVERTISE`.
  Android 11 이하용 `BLUETOOTH`, `BLUETOOTH_ADMIN`은 `maxSdkVersion="30"`.
- Foreground Service를 쓸 경우 Android 14+는 `foregroundServiceType="connectedDevice"`와
  `FOREGROUND_SERVICE_CONNECTED_DEVICE` 권한이 필요하고, Android 13+는 알림 권한도 필요하다.
- 마우스 X/Y는 int8(-127~127)이다. 큰 이동량은 여러 리포트로 나눠 보낸다.
- 키보드·Consumer 리포트는 반드시 "누름 → 뗌" 쌍으로 보낸다. 뗌을 빠뜨리면 PC에서 키가 눌린 채로 남는다.
