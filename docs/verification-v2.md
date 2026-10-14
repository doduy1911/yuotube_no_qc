# TubeLite V2 — Kết quả nghiệm thu

Ngày chạy: 2026-10-14. Môi trường UI: Android Emulator Pixel_7, Android 17 (API 36). Chưa thử trên điện thoại Android thật.

## Các bước đã kiểm tra

| Hạng mục | Cách kiểm tra | Kết quả |
| --- | --- | --- |
| Nhạc thịnh hành | Gọi YouTube Data API cho `regionCode=VN,US,GB` và danh mục Music | HTTP 200 cho cả ba vùng; API key chỉ nằm trong `.env` bị Git bỏ qua |
| Gợi ý từ khóa | Gõ `em cua`, chọn gợi ý Sơn Tùng; dán URL YouTube trên Pixel_7 | NewPipe trả gợi ý thật; chọn gợi ý tìm bằng Google; URL mở PlaybackActivity |
| Gợi ý video | Instrumentation gọi NewPipe related với video công khai `dQw4w9WgXcQ` | Có mục video liên quan hợp lệ; test service snapshot nhận được danh sách |
| Màn phát | Instrumentation xác nhận mục Video liên quan, nút toàn màn hình và thao tác Back | Nội dung ẩn trong toàn màn hình và phục hồi khi Back |
| Autoplay | Instrumentation chọn video công khai, nhận gợi ý, tua gần cuối và theo dõi MediaSession | Video kế tiếp được service tự resolve và chọn; công tắc tắt/bật lưu được qua SharedPreferences |
| Hủy resolve | Unit test điều phối | Tắt autoplay/chọn bài khác vô hiệu token cũ; sự kiện ended trùng không xử lý lần hai |
| Giới hạn ứng viên | Unit test điều phối | Tối đa ba ứng viên; không có mạng thì dừng; bỏ qua 50 ID gần nhất |
| API/TypeScript | Jest, ESLint và `tsc --noEmit` | 26 test qua; lint và typecheck qua |
| Android | Kotlin unit tests, instrumentation trên Pixel_7, debug và release assemble | Qua; instrumentation gồm Room, bố cục, NewPipe, autoplay và snapshot |

## Cài đè và dữ liệu V1

V1 và V2 dùng cùng `applicationId` (`com.tubelite`) và cùng debug signing certificate. SHA-256 chứng chỉ của APK V1 và APK V2: `fac61745dc0903786fb9ede62a962b399f7348f0bb6f899b8332667591033b9c`.

V2 tăng `versionCode` lên 2 và `versionName` lên 2.0. Instrumentation tạo history, vị trí 55 giây và favorite theo schema V1; sau khi cài đè APK V2, test xác nhận cả ba trường còn nguyên và autoplay mặc định bật. Không thực hiện nâng cấp trên điện thoại thật.

## APK

`android/app/build/outputs/apk/release/app-release.apk` — bản release đã assemble với key lấy từ `.env` tại thời điểm build. Không commit key hoặc APK có key người dùng vào Git.

## Chưa xác nhận trên thiết bị thật

- Xoay ngang và PiP đã có xử lý trong Activity nhưng chưa chạy thao tác xoay/PiP end-to-end trên máy thật.
- Chưa xác nhận notification/khóa màn hình trên thiết bị thật.
- Chưa kiểm tra đổi chất lượng từ UI khi đang pause bằng instrumentation; code giữ vị trí và `playWhenReady` khi thay stream.
- Không mô phỏng hết quota YouTube API hoặc mất mạng trên thiết bị thật. Unit tests kiểm tra nhánh không mạng/nguồn lỗi với fake.
- APK release hiện dùng debug keystore của dự án để cài thử; cần keystore phát hành riêng trước khi phát hành công khai.

Instrumentation đã xác nhận bố cục và Back/toàn màn hình; ảnh chụp emulator không được lưu thành artifact trong lượt nghiệm thu này.
