# TubeLite V2 — Implementation Plan

> **For agentic workers:** Dùng superpowers:executing-plans để triển khai tuần tự; đọc spec trước khi code. Các bước dùng checkbox để theo dõi bằng chứng hoàn thành.

**Goal:** Thêm nhạc thịnh hành, gợi ý tìm kiếm, màn phát dọc và tự phát bài kế tiếp cho TubeLite Android.

**Architecture:** React Native cho home/search/library; Kotlin repositories cho nguồn dữ liệu; PlaybackActivity hiển thị video và gợi ý bằng MediaController. PlaybackService giữ duy nhất một ExoPlayer và toàn bộ điều phối autoplay.

**Tech Stack:** React Native TypeScript, Kotlin, YouTube Data API, NewPipe Extractor, Media3, Room hiện có, SharedPreferences và cache JSON nội bộ.

**Spec:** [2026-10-14-tubelite-v2-design.md](../specs/2026-10-14-tubelite-v2-design.md)

**Trạng thái (2026-10-14):** Tasks 1–6 đã triển khai và commit riêng. Các kiểm tra còn bỏ trống bên dưới là kiểm tra thủ công chưa hoàn tất; xem `docs/verification-v2.md`.

## Global Constraints

- Android 8/API 26 trở lên; giao diện tiếng Việt, nền tối.
- Giữ dữ liệu lịch sử/yêu thích V1; không destructive migration hoặc xóa dữ liệu để cài V2.
- Một player trong service; autoplay không phụ thuộc JS timer.
- Tìm kiếm video và trending dùng Google; autocomplete và related dùng NewPipe.
- Không commit/log API key hoặc lưu URL nguồn phát lâu dài.
- Giữ dependency đã khóa; chỉ đổi khi có bằng chứng cần thiết và ghi lý do.
- Mỗi tính năng: test phù hợp → build → kiểm tra UI/hành vi → commit riêng. Không đánh dấu hoàn thành chỉ vì biên dịch thành công.

## Review Focus

- Nguồn trending lỗi một phần/cache cũ: vẫn thấy dữ liệu có thật và nguồn đúng (Task 1).
- Phản hồi autocomplete đến muộn sau khi xóa/submit: không mở lại danh sách cũ (Task 2).
- Related rỗng hoặc gồm livestream/trùng/bài đang phát: lọc và fallback có giới hạn (Task 3).
- Đổi màn/xoay/đổi chất lượng khi pause: một player, giữ vị trí và trạng thái (Task 4).
- Bài kết thúc trùng sự kiện hoặc chọn bài/tắt autoplay khi đang resolve: chỉ chuyển đúng một lần, lưu lịch sử đúng bài (Task 5).

## Cấu trúc mã dự kiến

- `src/features/home/HomeScreen.tsx`, `src/features/search/SearchBox.tsx`: UI được tách từ App theo từng task; giữ luồng tìm kiếm, dán link và thư viện hoạt động.
- `src/domain/musicDiscovery.ts`, `src/native/MusicDiscovery.ts`: kiểu logic/ánh xạ và wrapper cho bridge JSON.
- Trong `android/app/src/main/java/com/tubelite/`: thêm `TrendingMusicRepository.kt`, `MusicDiscoveryCache.kt`, `SearchSuggestionRepository.kt`, `RecommendationRepository.kt`, `PlaybackScreenState.kt`, `AutoplayCoordinator.kt`, `PlaybackSettings.kt` theo task tương ứng.
- Sửa `YouTubeSearchRepository`, `NewPipeVideoRepository`, `NativeVideoExtractorModule`, `PlaybackActivity`, `PlaybackService` để kết nối các phần; không gom tất cả logic mới vào Activity/service.
- Test TS cạnh feature/domain; test Kotlin tại `src/test` và instrumentation tại `src/androidTest` của Android app.

## Task 1 — Nhạc thịnh hành và trang chủ

**Interfaces:** wrapper `getTrendingMusic(section: 'vn' | 'international', forceRefresh: boolean): Promise<TrendingSection>`; native bridge trả JSON string. Repository Kotlin nhận section/forceRefresh và trả model tương ứng spec.

- [x] Viết test mapping thời lượng/metadata, ghép US/GB xen kẽ có dedupe và giới hạn 20; cache trong một giờ không gọi lại mạng; quá hạn cập nhật; một nguồn lỗi vẫn dùng phần có sẵn; không có cache hiện lỗi.
- [x] Chạy test để xác nhận chưa có hành vi mới; dùng clock và HTTP client có thể thay bằng fake để kiểm tra cache, không dùng key thật trong test.
- [x] Tạo trending repository/cache; chia sẻ HTTP/error mapping của Google repository hiện có. Xác minh danh mục Music trên nguồn thật; lưu metadata theo vùng, không lưu key hoặc URL nguồn phát.
- [x] Tạo kiểu/wrapper TS theo spec, thêm bridge và tách HomeScreen; hai mục tải độc lập, pull-to-refresh, stale/error/empty state; chạm bài dùng luồng mở video hiện có.
- [x] Chạy test liên quan, lint/TypeScript và assembleDebug; kiểm tra hai mục thật trên emulator, chụp UI và ghi trường hợp quota/mạng/cache đã kiểm tra.
- [x] Commit `feat: add Vietnam and international trending music`.

## Task 2 — Gợi ý từ khóa tìm kiếm

**Interfaces:** `getSearchSuggestions(query: string): Promise<string[]>`; native bridge JSON string qua wrapper, NewPipe `suggestionList(query)` chạy trên executor riêng để không chặn mở video.

- [x] Viết test: dưới hai ký tự không gọi nguồn; debounce 300 ms; tối đa tám cụm từ duy nhất; A đến sau B bị bỏ; xóa/submit vô hiệu phản hồi; chọn gợi ý chỉ submit một lần; lỗi gợi ý không chặn tìm thủ công; link hợp lệ bỏ qua autocomplete.
- [x] Chạy test thất bại trước khi thêm hành vi; dùng fake timers và Promise có thể điều khiển thứ tự trả về.
- [x] Thêm repository/bridge/wrapper và tách SearchBox. Giữ search.list chỉ chạy khi submit/chạm gợi ý; dùng generation ID cho cả autocomplete và kết quả tìm kiếm.
- [x] Chạy test, lint/TypeScript và assembleDebug; thử gõ tiếng Việt, chọn gợi ý, nhập/xóa nhanh và dán link trên emulator; chụp UI.
- [x] Commit `feat: add live search suggestions`.

## Task 3 — Nguồn bài gợi ý

**Interfaces:** native `getRecommendations(videoId: String): RecommendationSet`; `RecommendationSet` chứa videoId, items và source theo spec. Native dùng cùng VideoSummary cho UI và autoplay.

- [x] Viết test lọc mục không phải video/live/upcoming, ID hiện tại, ID trùng; related rỗng/lỗi chuyển sang trending; fallback xen kẽ VN/quốc tế; hai nguồn đều lỗi trả trạng thái rỗng/lỗi hữu hạn.
- [x] Chạy test thấy thiếu hành vi; thêm RecommendationRepository và mở rộng NewPipe repository bằng getRelatedItems(). Tái sử dụng StreamInfo trong một lần resolve khi có thể, không giữ URL token trong cache bền vững.
- [x] Giới hạn 20 ứng viên, giữ thứ tự nguồn, trả nhãn related/trending đúng dữ liệu. Mỗi yêu cầu gắn video ID để bên nhận bỏ phản hồi cũ.
- [x] Chạy test và assembleDebug; kiểm tra ít nhất một video công khai có related thực. Dùng fake nguồn để chứng minh fallback/lỗi, phân biệt với thử nghiệm trực tuyến.
- [x] Commit `feat: add related video recommendations`.

## Task 4 — Màn phát dọc

**Interfaces:** `PlaybackScreenState` chứa currentVideo/status/positionMs/autoplayEnabled/recommendations/recommendationsLoading/error. MediaSession: GET_PLAYBACK_SCREEN_STATE, SELECT_VIDEO(videoId), SET_AUTOPLAY(enabled), sự kiện PLAYBACK_SCREEN_STATE_CHANGED. Task này bổ sung snapshot, chọn bài và hiển thị; hành vi/công tắc autoplay hoàn thiện tại Task 5.

- [ ] Viết test cho kết nối UI lấy snapshot hiện tại, đổi bài bỏ metadata/gợi ý cũ, chọn bài liên tiếp chỉ mở lựa chọn cuối; đổi chất lượng giữ pause và vị trí; Back toàn màn hình không đổi phiên.
- [x] Sửa Activity thành bố cục video 16:9 ở trên, thông tin/yêu thích và danh sách cuộn bên dưới. Dùng dp/window insets thay vị trí nút cố định bằng pixel; video dọc fit trong khung.
- [x] Đưa luồng chọn video của bridge và Activity về cùng cơ chế request generation tại native. Service phát snapshot sau đổi bài/trạng thái/gợi ý; Activity đăng ký/hủy listener theo lifecycle.
- [x] Toàn màn hình và xoay chỉ đổi layout/view gắn cùng MediaController; PiP ẩn phần thông tin/gợi ý, thoát PiP phục hồi bố cục. Danh sách có loading/error/empty và nhãn Khám phá thêm khi fallback.
- [x] Chụp video ID/metadata/vị trí trước khi lưu bất đồng bộ; không đọc currentMetadata có thể đã đổi trong executor. Sửa giữ playWhenReady khi đổi chất lượng.
- [ ] Chạy test và assembleDebug; kiểm tra màn dọc, ngang, Back, PiP và đổi bài nhanh trên emulator; kiểm tra một MediaSession/player và chụp các trạng thái UI.
- [x] Commit `feat: add portrait watch screen with recommendations`.

## Task 5 — Tự phát bài kế tiếp

**Interfaces:** `AutoplayCoordinator` nhận ended, danh sách ứng viên, lựa chọn thủ công và enabled; phát quyết định resolve/stop. Service giữ coordinator; `PlaybackSettings` lưu boolean mặc định true. Hoàn thiện SET_AUTOPLAY và công tắc từ Task 4.

- [x] Viết test enabled/disabled, ghi nhớ sau mở lại, chọn ứng viên đầu tiên hợp lệ, bắt đầu bài tự chuyển tại 0, tránh 50 bài gần nhất, cho phép chọn lại thủ công, hết danh sách dừng.
- [ ] Viết test ended phát hai lần chỉ chuyển một lần; pause/quality/PiP không chuyển bài; tắt autoplay/chọn bài mới trong lúc resolve làm kết quả cũ mất hiệu lực; chờ related chưa về chỉ quyết định một lần.
- [ ] Viết test tối đa ba ứng viên lỗi mỗi lần chuyển, mất mạng dừng ngay, source-expiry retry không bị coi là ended. Test lưu cuối bài cũ và vị trí bài mới không ghi chéo ID.
- [x] Chạy test trước khi triển khai; thêm coordinator/settings, dùng sự kiện Player.STATE_ENDED trong service, không tạo timer JS hoặc player thứ hai.
- [x] Resolve URL khi cần phát, không preload toàn bộ stream URL. Khi ứng viên bị loại/lỗi, cập nhật snapshot danh sách; dùng lại metadata repository và giới hạn request của spec.
- [x] Hoàn thiện công tắc và trạng thái đang chuyển/lỗi; sync snapshot khi Activity kết nối lại. Notification cập nhật metadata của bài mới; service xử lý ngay cả khi Activity không tồn tại.
- [ ] Chạy test và assembleDebug; thử hết bài bằng tua gần cuối, chuyển trong nền/khóa màn/PiP, tắt công tắc lúc đang tải, mở lại app và lịch sử/yêu thích trên emulator.
- [x] Commit `feat: add persistent autoplay and background next track`.

## Task 6 — Nghiệm thu và APK V2

- [x] Cập nhật Android versionCode=2, versionName=2.0; giữ applicationId com.tubelite và chữ ký tương thích APK V1 để cài đè.
- [x] Chạy `npm test -- --runInBand`, `npm run lint`, `npx tsc --noEmit` và trong android chạy `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleRelease`; chỉ đánh dấu qua khi xem được kết quả.
- [x] Cài V1 trên thiết bị kiểm thử, tạo lịch sử/yêu thích rồi cài đè APK V2 không xóa app; kiểm tra dữ liệu được giữ và công tắc autoplay mặc định lần đầu.
- [ ] Kiểm tra lại các mục nghiệm thu spec, gồm mạng mất, nguồn lỗi, đổi truy vấn nhanh, tiếp tục đúng vị trí, bài kết thúc phát lại từ đầu và đổi chất lượng khi pause.
- [ ] Cài đúng APK release và kiểm tra khi Metro không chạy/không có adb reverse: mở home, tìm bài, phát, gợi ý và autoplay. Xác minh foreground notification, khóa màn hình, xoay và PiP.
- [ ] Ghi bằng chứng, ảnh UI và giới hạn thiết bị thật trong `docs/verification-v2.md`; cập nhật README hướng dẫn build/cài/thay key và vị trí APK. Không đưa key vào ảnh/log/tài liệu.
- [x] Kiểm tra git diff/staged, commit `docs: document V2 APK and verification`; phát hành GitHub V2 chỉ khi người dùng yêu cầu.

## Quy tắc đánh dấu hoàn thành

Test tự động phải kiểm tra hành vi, không chỉ giả lập đúng cùng thuật toán của implementation. Ghi riêng nguồn giả, nguồn trực tuyến, emulator và thiết bị thật. Nếu NewPipe hoặc chart không hoạt động thực tế, xác định nguyên nhân trước khi coi tính năng đạt nghiệm thu.

**Trạng thái thực hiện:** Tasks 1–6 đã được triển khai và commit theo từng phần. Sau lượt review, đã bổ sung sửa lỗi công tắc autoplay, stale request/generation, nguồn fallback, lưu vị trí video kết thúc và bố cục toàn màn hình; kiểm tra lại bằng Jest, ESLint, TypeScript, Kotlin unit tests, 10 instrumentation tests trên Pixel_7 và release assemble. Các bước xoay/PiP end-to-end, notification/khóa màn hình trên thiết bị thật, đổi chất lượng khi pause, mất mạng thực tế và screenshot artifact vẫn chưa được xác nhận; xem `docs/verification-v2.md`.
