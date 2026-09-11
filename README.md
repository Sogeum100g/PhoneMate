<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="128" alt="PhoneMate 앱 아이콘">
  <h1>PhoneMate</h1>
  <p>배터리와 네트워크 상태를 화면 위 캐릭터의 움직임으로 보여주는 Android 메이트</p>

  <a href="https://play.google.com/store/apps/details?id=com.phonemate.android&hl=ko">
    <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="80" alt="Google Play에서 다운로드">
  </a>
</div>

## 소개

PhoneMate는 스마트폰 상태를 숫자 대신 살아 있는 캐릭터의 움직임으로 보여주는 플로팅 오버레이 앱입니다. 다른 앱을 사용하는 동안에도 화면 위 캐릭터를 통해 배터리 잔량이나 현재 네트워크 속도를 자연스럽게 확인할 수 있습니다.

배터리가 넉넉하거나 네트워크가 빠르면 캐릭터가 활기차게 달리고, 상태가 낮아지면 걷거나 멈추고 눕습니다. 콘텐츠가 로딩되는 짧은 순간에는 화면 위의 작은 친구가 기다림의 지루함도 덜어 줍니다.

<img width="300" height="650" alt="image" src="https://github.com/user-attachments/assets/eb65a609-e482-4c41-b3ff-d32608f8ea3b" />

<img width="300" height="650" alt="image" src="https://github.com/user-attachments/assets/6b182f3d-9d97-49a2-aadd-0d6329b02253" />

<img width="300" height="650" alt="image" src="https://github.com/user-attachments/assets/39eddfa4-bb11-453b-8d29-c4b255f207ac" />



## 주요 기능

- 다른 앱 위를 돌아다니는 플로팅 캐릭터 오버레이
- 배터리 잔량 또는 네트워크 속도에 따른 4단계 상태 애니메이션
- 기본 제공되는 Blob 캐릭터
- GIF, PNG, JPG, JPEG 이미지로 만드는 커스텀 캐릭터
- 캐릭터 크기, 위치, 이미지 모양과 애니메이션 속도 설정
- 상태 단계별 경곗값과 리소스 표시 방식 조절
- 회원가입 없이 바로 사용할 수 있는 로컬 중심 설계

모든 설정과 커스텀 캐릭터 파일은 사용자의 기기 내부에 저장됩니다.

## 사용 방법

1. 앱을 실행하고 다른 앱 위에 표시 권한을 허용합니다.
2. Android 13 이상에서는 알림 권한도 허용합니다.
3. 메인 화면에서 모니터링할 항목과 캐릭터를 선택합니다.
4. `시작`을 누르면 캐릭터가 화면 위에 나타납니다.
5. 캐릭터를 길게 눌러 빠른 설정 메뉴를 열 수 있습니다.

## 기술 구성

- Kotlin
- Jetpack Compose
- Kotlin Coroutines
- AndroidX DataStore Preferences
- Android Foreground Service 및 Overlay API
- android-gif-drawable
- Android 및 Wear OS 모듈

지원하는 최소 Android 버전은 Android 8.0(API 26)입니다.

## 개발 및 빌드

Android SDK와 JDK 17이 필요합니다. 저장소를 받은 후 Gradle Wrapper로 빌드하고 테스트할 수 있습니다.

Windows:

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

## Google Play

PhoneMate는 [Google Play에서 설치](https://play.google.com/store/apps/details?id=com.phonemate.android&hl=ko)할 수 있습니다.
