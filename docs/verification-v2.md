# TubeLite V2 — Kết quả nghiệm thu

Ngày chạy: 2026-10-14. Môi trường UI: Android Emulator Pixel_7, Android 17 (API 36). Chưa thử trên điện thoại Android thật.

## Các bước đã kiểm tra

| Hạng mục | Cách kiểm tra | Kết quả |
| --- | --- | --- |
| Nhạc thịnh hành | Gọi YouTube Data API cho `regionCode=VN,US,GB` và danh mục Music | HTTP 200 cho cả ba vùng; API key chỉ nằm trong `.env` bị Git bỏ qua |
| Gợi ý từ khóa | Gõ `em cua`, chọn gợi ý Sơn Tùng; dán URL YouTube trên Pixel_7 | NewPipe trả gợi ý thật; chọn gợi ý tìm bằng Google; URL mở PlaybackActivity |
| Gợi ý video | Instrumentation gọi NewPipe related với video công khai `dQw4w9WgXcQ` | Có mục video liên quan hợp lệ; test service snapshot nhận được danh sách |
| Màn phát | Instrumentation xác nhận mục Video liên quan, nút toàn màn hình và thao tác Back | Nội dung ẩn trong toàn màn hình và phục hồi khi Back |
| Autoplay | Instrumentation chọn video công khai, nhận gợi ý, tua gần cuối và theo dõi MediaSession | Video kế tiếp được service tự resolve và chọn; công tắc UI đổi Bật/Tắt cả hai chiều |
| Hủy resolve | Unit test điều phối và instrumentation | Tắt autoplay, chọn bài khác, Stop và token lỗi cũ vô hiệu hóa kết quả tương ứng; ended trùng không xử lý lần hai |
| Giới hạn ứng viên/fallback | Unit test điều phối | Thử tối đa ba ứng viên; fallback trending sau khi related hết ứng viên mới dùng được; bỏ qua 50 ID gần nhất; không có mạng thì dừng |
| Lịch sử đã nghe | Kiểm tra các nhánh lưu vị trí trong service và unit test | Video ở trạng thái ended được lưu lại vị trí 0 kể cả lúc service dừng hoặc đổi bài |
| Reviewer pass | Rà soát lại các thay đổi trong nhánh V2 | Sửa toggle, trạng thái lựa chọn video, đổi chất lượng cũ, nguồn gợi ý, tỷ lệ màn hình và điều phối fallback; không còn lỗi chặn |
| API/TypeScript | Jest, ESLint và `tsc --noEmit` | 26 test qua; lint và typecheck qua |
| Android | Kotlin unit tests, 10 instrumentation tests trên Pixel_7, debug và release assemble | Qua; instrumentation gồm Room, nâng cấp dữ liệu, bố cục, NewPipe related và autoplay MediaSession |

## Cài đè và dữ liệu V1

V1 và V2 dùng cùng `applicationId` (`com.tubelite`) và cùng debug signing certificate. SHA-256 chứng chỉ của APK V1 và APK V2: `fac61745dc0903786fb9ede62a962b399f7348f0bb6f899b8332667591033b9c`.

V2 tăng `versionCode` lên 2 và `versionName` lên 2.0. Instrumentation tạo history, vị trí 55 giây và favorite theo schema V1; sau khi cài đè APK V2, test xác nhận cả ba trường còn nguyên và autoplay mặc định bật. Không thực hiện nâng cấp trên điện thoại thật.

## APK

`android/app/build/outputs/apk/release/app-release.apk` — bản release đã assemble trong repo chính với key lấy từ `.env`; dung lượng 56 MB. SHA-256: `9564d070e32c1d94476c95677af32dad82004e7da4b2d17c39f775b885f31631`. APK và `.env` đều bị Git bỏ qua. Chữ ký APK vẫn khớp V1 (`fac61745dc0903786fb9ede62a962b399f7348f0bb6f899b8332667591033b9c`).

## Chưa xác nhận trên thiết bị thật

- Xoay ngang và PiP đã có xử lý trong Activity nhưng chưa chạy thao tác xoay/PiP end-to-end trên máy thật.
- Chưa xác nhận notification/khóa màn hình trên thiết bị thật.
- Chưa kiểm tra đổi chất lượng từ UI khi đang pause bằng instrumentation; code giữ vị trí và `playWhenReady` khi thay stream.
- Không mô phỏng hết quota YouTube API hoặc mất mạng trên thiết bị thật. Unit tests kiểm tra nhánh không mạng/nguồn lỗi với fake.
- APK release hiện dùng debug keystore của dự án để cài thử; cần keystore phát hành riêng trước khi phát hành công khai.

Instrumentation đã xác nhận bố cục và Back/toàn màn hình; ảnh chụp emulator không được lưu thành artifact trong lượt nghiệm thu này.
