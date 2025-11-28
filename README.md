# SNS Auto

Flutter + Android 기반 SNS 자동 영상 생성 및 업로드 앱

## 프로젝트 개요

SNS Auto는 여러 장의 사진을 선택하면 자동으로 슬라이드쇼 영상을 생성하고, Facebook, Instagram, YouTube, TikTok 등 여러 SNS 플랫폼에 업로드할 수 있는 앱입니다.

## 주요 기능

### 1. 영상 생성 (Android Native 인코딩)
- **해상도**: 1080×1920 (세로형, SNS Shorts/Reels 최적화)
- **프레임레이트**: 30 FPS
- **비디오 코덱**: H.264 (AVC)
- **오디오 코덱**: AAC (44.1kHz, 스테레오)
- **전환 효과**: 크로스페이드 (0.5초)
- **이미지 표시 시간**: 1.5초

### 2. 배경음악 (BGM) 지원
- MP3 파일을 사용하여 배경음악 추가 가능
- 영상 길이에 맞춰 자동 트림
- 마지막 1초 페이드아웃 효과

#### 📁 BGM 파일 위치
```
/Android/data/com.example.sns_auto/files/bgm/bgm_default.mp3
```

#### 📝 BGM 파일 추가 방법
1. Android 기기에서 파일 관리자를 엽니다
2. 위 경로로 이동합니다 (앱 실행 시 자동으로 디렉토리가 생성됩니다)
3. `bgm_default.mp3` 이름으로 MP3 파일을 복사합니다
4. 앱에서 영상을 렌더링하면 자동으로 배경음악이 적용됩니다

**참고**: BGM 파일이 없으면 무음으로 영상이 생성됩니다.

### 3. 템플릿 시스템
- **기본 슬라이드쇼 (클래식)**: 부드러운 크로스페이드 전환 효과
- 향후 추가 템플릿 확장 가능한 구조

### 4. 다중 플랫폼 업로드
- Facebook 페이지
- Instagram 릴스
- YouTube Shorts
- TikTok

## 기술 스택

### Frontend
- **Flutter** (Dart)
- **Material Design 3**

### Backend (Android Native)
- **MediaCodec** - 비디오/오디오 인코딩
- **MediaMuxer** - MP4 컨테이너 생성
- **MediaExtractor** - 오디오 디코딩
- **Kotlin**

### Platform Communication
- **Method Channel** (`sns_auto/video_encoder`)

## 프로젝트 구조

```
SNS_auto/
├── android/
│   └── app/src/main/kotlin/com/example/sns_auto/
│       ├── MainActivity.kt          # MethodChannel 처리
│       └── VideoEncoder.kt          # Native 비디오 인코더
├── lib/
│   ├── config/
│   │   ├── app_config.dart
│   │   └── constants.dart           # 상수 및 UI 텍스트 (한국어)
│   ├── models/
│   │   ├── render_job.dart
│   │   ├── template_model.dart      # 템플릿 모델
│   │   └── upload_target.dart       # 업로드 설정
│   ├── screens/
│   │   ├── home_screen.dart         # 홈 화면 (사진/템플릿 선택)
│   │   ├── render_screen.dart       # 렌더링 화면
│   │   └── upload_screen.dart       # 업로드 화면
│   ├── services/
│   │   ├── video_renderer.dart      # 비디오 렌더링 서비스
│   │   ├── media_picker_service.dart
│   │   └── sns/                     # SNS 업로드 서비스
│   └── widgets/
└── README.md
```

## 설치 및 실행

### 요구사항
- Flutter SDK (3.0 이상)
- Android Studio
- Android SDK (API Level 21 이상)

### 실행 방법
```bash
# 의존성 설치
flutter pub get

# Android 기기/에뮬레이터에서 실행
flutter run
```

## 사용 방법

### 1️⃣ 사진 선택
- 홈 화면에서 "사진 선택" 버튼을 탭합니다
- 갤러리에서 영상에 포함할 사진들을 선택합니다

### 2️⃣ 템플릿 선택
- "기본 슬라이드쇼 (클래식)" 템플릿을 선택합니다

### 3️⃣ 영상 생성
- "영상 생성" 버튼을 탭합니다
- 렌더링 진행 상황을 확인합니다
- 완성된 영상을 미리보기합니다

### 4️⃣ SNS 업로드 (선택사항)
- 업로드할 플랫폼을 선택합니다
- 캡션과 태그를 입력합니다
- "업로드" 버튼을 탭합니다

## 최근 업데이트

### 2025-01 (현재 버전)
#### ✅ Android VideoEncoder 안정화
- MediaMuxer CSD (Codec Specific Data) 처리 표준화
- INFO_OUTPUT_FORMAT_CHANGED 이벤트 기반 트랙 추가
- MediaMuxer.stop() -1007 에러 수정
- EndOfStream 처리 안정화

#### ✅ BGM 기능 추가
- 외부 저장소에서 MP3 파일 로드
- 오디오 트랙 병합 (AAC 인코딩)
- 마지막 1초 페이드아웃 효과
- 영상 길이에 맞춰 자동 트림

#### ✅ UI 한국어 전체 적용
- 모든 화면 텍스트 한글화
- 버튼, 레이블, 안내 메시지
- Toast, Snackbar 메시지
- 로딩/오류 메시지

#### ✅ 템플릿 구조 단순화
- 단일 템플릿으로 정리: "기본 슬라이드쇼 (클래식)"
- 사용하지 않는 템플릿 로직 제거
- 향후 확장 가능한 구조 유지

## 테스트 방법

### 기본 테스트 시나리오
1. **영상 생성 테스트 (BGM 없음)**
   - 사진 3~5장 선택
   - 기본 템플릿 선택
   - 영상 생성 버튼 클릭
   - ✅ 성공 기준: 영상 재생 가능, 크로스페이드 정상 동작

2. **영상 생성 테스트 (BGM 있음)**
   - BGM 파일을 지정 경로에 복사
   - 사진 3~5장 선택
   - 영상 생성
   - ✅ 성공 기준: 영상 재생 시 배경음악 들림, 마지막 1초 페이드아웃

3. **크래시 테스트**
   - 다양한 이미지 개수 (1장, 10장)
   - 다양한 이미지 해상도
   - ✅ 성공 기준: 앱 크래시 없음

## 라이선스

이 프로젝트는 개인 프로젝트입니다.

## 문의

프로젝트 관련 문의사항은 GitHub Issues를 통해 제출해주세요.
