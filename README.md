# TubeLite

TubeLite là ứng dụng Android React Native để tìm và xem video YouTube không chèn quảng cáo trong giao diện app. Trang chủ có nhạc thịnh hành Việt Nam và quốc tế; tìm kiếm có gợi ý; màn phát có video liên quan và tự phát bài kế tiếp.

## Yêu cầu

- Node.js và npm theo cấu hình dự án.
- Android Studio, Android SDK và JDK 17.
- Android 8.0 (API 26) trở lên.
- YouTube Data API key có quyền dùng YouTube Data API v3 để tải tìm kiếm và nhạc thịnh hành.

## Cấu hình API key

Tạo `.env` ở thư mục gốc dự án từ mẫu rồi nhập key cục bộ:

```sh
cp .env.example .env
```

Đặt `YOUTUBE_API_KEY=...` trong `.env`. File `.env` được Git bỏ qua. Key được nhúng vào APK tại thời điểm build; không chia sẻ APK có key riêng tư và hãy giới hạn key theo hướng dẫn Google Cloud.

## Chạy và kiểm tra

```sh
npm install
npm start
```

Ở terminal khác:

```sh
npm run android
```

Các kiểm tra dự án:

```sh
npm test -- --runInBand
npm run lint
npx tsc --noEmit
cd android
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

`connectedDebugAndroidTest` cần emulator hoặc thiết bị Android đã bật USB debugging.

## Tạo APK release

```sh
cd android
./gradlew :app:assembleRelease
```

APK V2 được tạo tại `android/app/build/outputs/apk/release/app-release.apk`. Bản hiện tại dùng cùng debug keystore cục bộ như APK V1 trong repository, để thử cài đè V1 trên máy này. Đây không phải chữ ký phù hợp để phát hành công khai; muốn phát hành chính thức, cần cấu hình keystore riêng được quản lý an toàn và giữ ổn định qua các lần phát hành.

## Cài APK

```sh
adb install -r android/app/build/outputs/apk/release/app-release.apk
```

Gỡ app sẽ xóa lịch sử và yêu thích lưu trên thiết bị. Cài APK mới bằng `adb install -r` giữ dữ liệu ứng dụng nếu chữ ký tương thích.

## Tài liệu

- [Đặc tả V2](docs/superpowers/specs/2026-10-14-tubelite-v2-design.md)
- [Kế hoạch V2](docs/superpowers/plans/2026-10-14-tubelite-v2-plan.md)
- [Kết quả nghiệm thu V2](docs/verification-v2.md)
