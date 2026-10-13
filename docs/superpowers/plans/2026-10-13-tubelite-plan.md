# TubeLite — Implementation Plan

> For agentic workers: sử dụng superpowers:executing-plans để triển khai tuần tự. Đọc spec trước khi code; đánh dấu từng mục sau khi có bằng chứng kiểm tra.

**Goal:** Xây APK Android React Native tìm kiếm YouTube, phát riêng, nghe nền và PiP cho sử dụng cá nhân.

**Architecture:** React Native TypeScript cho giao diện; Turbo Native Module Kotlin cho Google Search, NewPipe Extractor và lưu trữ. Media3 trong MediaSessionService giữ một player duy nhất; Fabric component hiển thị video.

**Tech Stack:** React Native Community CLI, TypeScript, Kotlin, Media3 ExoPlayer, NewPipe Extractor, Room, Gradle.

**Spec:** ../specs/2026-10-13-tubelite-design.md

## Global Constraints

- Android 8/API 26 trở lên; bản đầu chỉ Android, tiếng Việt, nền tối.
- API key đọc từ .env lúc build; không commit hoặc log key.
- Tìm kiếm bằng Google; trích xuất bằng NewPipe; không tự đổi nguồn tìm kiếm.
- Một player duy nhất sống trong service; vị trí được lưu từ native, không dựa vào JS timer.
- Commit từng chức năng sau khi kiểm tra; không đụng file IDE của người dùng.
- Giữ phiên bản dependency cố định và thông tin giấy phép; không dùng snapshot tự thay đổi.

## Review Focus

- Kết quả truy vấn cũ đến muộn: không ghi đè truy vấn mới (Task 3).
- Chuyển UI/PiP/nền liên tục: không có hai player hoặc mất phiên phát (Task 4).
- Hình/tiếng tách riêng và URL hết hạn: đồng bộ, phục hồi giới hạn một lần (Task 2, 4).
- Xóa lịch sử của video yêu thích: yêu thích vẫn tồn tại (Task 5).
- Key thiếu/sai hoặc quota hết: lỗi rõ, dán link vẫn dùng được (Task 1, 3).

## Cấu trúc mã dự kiến

- src/features/home, search, player, library: màn hình, hooks và trạng thái theo chức năng.
- src/native: các spec Codegen cho extractor/search/player/library và native video view.
- src/domain: kiểu dữ liệu và chuẩn hóa link YouTube.
- android/app/src/main/java/com/tubelite/: search, extractor, playback, storage, bridge.
- docs/: spec, kế hoạch và hướng dẫn bàn giao; tests đặt cùng nhóm chức năng tương ứng.

## Task 1 — Tài liệu, cấu hình và khung ứng dụng

- [ ] Lưu spec và plan, tự đối chiếu phạm vi rồi commit `docs: add TubeLite specification and implementation plan`.
- [ ] Khởi tạo React Native TypeScript trong kho hiện tại; applicationId com.tubelite, minSdk 26. Giữ file người dùng hiện có.
- [ ] Khóa bộ phiên bản ổn định tương thích tại thời điểm triển khai và ghi trong lockfile/version catalog.
- [ ] Tạo .env bằng key được người dùng cung cấp; .env.example chỉ có YOUTUBE_API_KEY=; cấu hình Git bỏ qua .env, node_modules, build artifacts và .idea.
- [ ] Đọc .env từ Gradle, đưa key vào BuildConfig; thiếu key không làm crash ứng dụng.
- [ ] Tạo khung màn hình tối tiếng Việt; chuẩn bị Codegen và native package.
- [ ] Kiểm tra build, TypeScript và cấu hình không key; kiểm tra git diff/staged không có key.
- [ ] Commit `chore: bootstrap React Native Android app and environment config`.

## Task 2 — Trích xuất và luồng phát tối thiểu

**Interfaces:** VideoSummary, VideoDetails, getVideo(videoId), load(videoId, startPositionMs?), play, pause, seekTo, stop và playbackState theo spec.

- [ ] Viết kiểm tra chuẩn hóa watch/youtu.be/shorts URL, link sai và domain giả; chạy thấy lỗi trước khi hiện thực.
- [ ] Tích hợp NewPipe Extractor, downloader, desugaring và lỗi typed trên background dispatcher.
- [ ] Tạo MediaSessionService/ExoPlayer và Fabric video view, kết nối qua MediaController.
- [ ] Triển khai dán link → video ID → lấy nguồn → phát; hỗ trợ progressive và hình/tiếng tách riêng.
- [ ] Kiểm tra trên emulator-5554 với video công khai: có hình, tiếng, tua được; ghi ID mẫu và kết quả, không ghi URL có token.
- [ ] Nếu extractor lỗi, xác định nguyên nhân và sửa trước khi chuyển sang giao diện đầy đủ; không thay bằng dữ liệu giả rồi coi là thành công.
- [ ] Commit `feat: play YouTube videos through native extraction and Media3`.

## Task 3 — Tìm kiếm Google

**Interfaces:** searchVideos(query, pageToken?): Promise<SearchPage>; SearchPage và VideoSummary theo spec.

- [ ] Viết test ánh xạ kết quả, thời lượng ISO 8601, phân trang và lỗi key/quota/mạng.
- [ ] Gọi search.list với 20 video/trang; gọi videos.list theo lô bổ sung thời lượng. Chuyển lỗi API thành code/message chuẩn.
- [ ] Xây màn hình tìm kiếm, danh sách kết quả, loading/empty/error và tải thêm.
- [ ] Gửi truy vấn khi submit; dùng request generation để bỏ phản hồi truy vấn cũ; khóa tải trùng trang.
- [ ] Test truy vấn A phản hồi sau B không ghi đè B; thử API thực với truy vấn nhỏ, không lặp hàng loạt gây hao quota.
- [ ] Kiểm tra chạm kết quả mở đúng video; thiếu key vẫn dùng dán link được.
- [ ] Commit `feat: add Google YouTube search and pagination`.

## Task 4 — Player đầy đủ, nghe nền và PiP

**Interfaces:** bổ sung setQuality(qualityId), enterPip(); sự kiện playbackState đầy đủ.

- [ ] Viết kiểm tra chọn chất lượng mặc định, giữ vị trí khi đổi nguồn và chỉ phục hồi nguồn hết hạn một lần.
- [ ] Xây điều khiển React Native: phát/dừng, tua, chất lượng, toàn màn hình và trạng thái buffering/lỗi.
- [ ] Chọn mặc định cao nhất không quá 720p, nếu không có thì chất lượng thấp nhất; chỉ hiển thị nguồn dùng được.
- [ ] Khi nguồn hết hạn, resolve lại trong native và tiếp tục tại vị trí cũ một lần; thất bại hiện thử lại thủ công.
- [ ] Cấu hình foreground service, media notification, quyền Android và audio focus; pause khi ngắt tai nghe.
- [ ] Triển khai PiP theo cùng media session, tỷ lệ khung hình của video; thoát PiP về UI giữ trạng thái.
- [ ] Kiểm tra trên máy ảo: Home, khóa màn hình, xoay, PiP, điều khiển notification và quay lại app không tạo player thứ hai.
- [ ] Ghi phần cần thiết bị thật, đặc biệt hành vi tai nghe/audio focus nếu máy ảo không mô phỏng đầy đủ.
- [ ] Commit `feat: add playback controls background audio and picture in picture`.

## Task 5 — Lịch sử và yêu thích

**Interfaces:** getHistory, getFavorites, setFavorite(videoId, enabled), clearHistory; dữ liệu theo spec.

- [ ] Viết Room tests cho upsert theo video ID, persistence và xóa lịch sử không mất yêu thích.
- [ ] Tạo Room database; lưu metadata, vị trí, thời điểm xem, trạng thái yêu thích.
- [ ] Service lưu vị trí mỗi 5 giây và khi pause/stop/chuyển video, không dựa vào JavaScript.
- [ ] Xây màn hình thư viện, danh sách xem gần đây trên trang chính và trạng thái trống.
- [ ] Mở lại video tiếp tục vị trí lưu; video đã kết thúc bắt đầu lại từ đầu khi người dùng mở lần nữa.
- [ ] Kiểm tra đóng/mở app, yêu thích trùng, xóa lịch sử và phát nền cập nhật vị trí.
- [ ] Commit `feat: persist watch history favorites and resume position`.

## Task 6 — Kiểm tra tổng thể và APK

- [ ] Chạy TypeScript, lint, unit tests và Android tests đã bổ sung; sửa lỗi thực trước khi đóng gói.
- [ ] Build APK standalone có JS bundle, cài lên emulator-5554 và kiểm tra khi Metro không chạy.
- [ ] Thực hiện các tình huống nghiệm thu trong spec, bao gồm mất mạng, link sai, lỗi nguồn và điều hướng nhanh.
- [ ] Kiểm tra manifest, quyền, vòng đời service, dữ liệu/log và staged diff không lộ key.
- [ ] Viết README hướng dẫn build/cài/thay key, giấy phép phụ thuộc, vị trí APK và giới hạn đã biết.
- [ ] Ghi bằng chứng kiểm thử trong docs/verification.md; phân biệt kiểm tra tự động, máy ảo và thiết bị thật chưa làm.
- [ ] Commit `docs: document APK delivery and verification results`; lỗi sửa được commit theo chức năng liên quan.

## Tiêu chí hoàn thành

APK tự chạy không cần Metro; luồng tìm kiếm và dán link phát được video công khai; player, nghe nền, PiP và thư viện đạt kiểm tra đã nêu. Không gọi tính năng hoàn thành nếu chỉ build thành công mà chưa kiểm tra hành vi. Mọi giới hạn hoặc bước chưa xác minh phải được ghi trong bàn giao.
