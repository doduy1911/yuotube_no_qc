# TubeLite — Đặc tả sản phẩm và kỹ thuật

Ngày: 2026-10-13
Trạng thái: Thiết kế đã thống nhất, chưa triển khai ứng dụng.

## Mục tiêu

App Android React Native dùng cá nhân, cài bằng APK, giao diện quen thuộc kiểu YouTube. Tìm kiếm bằng YouTube Data API, phát video bằng trình phát riêng với nguồn từ NewPipe Extractor. Mục tiêu không có quảng cáo do YouTube chèn; đoạn tài trợ có sẵn trong nội dung video vẫn giữ nguyên. Khả năng trích xuất/phát phải được kiểm tra thực tế và có thể cần cập nhật khi YouTube thay đổi.

## Nền tảng và phạm vi

- React Native Community CLI, TypeScript; giao diện tiếng Việt, nền tối, tên tạm TubeLite.
- Android 8/API 26 trở lên; Kotlin cho tích hợp Android, không triển khai iOS trong bản đầu.
- Không cần backend. Không đăng nhập Google, đồng bộ tài khoản, tải video, livestream hoặc luồng Shorts riêng trong bản đầu.
- Mã ứng dụng và biểu tượng riêng; không giả mạo ứng dụng YouTube chính thức.

## Trải nghiệm người dùng

1. Trang chính có ô tìm kiếm/dán link, lịch sử gần đây và hướng dẫn khi chưa có dữ liệu.
2. Tìm kiếm khi người dùng nhấn gửi, không gọi API theo từng phím. Kết quả gồm ảnh, tiêu đề, tên kênh, thời lượng và tải thêm khi cuộn.
3. Dán link youtube.com/watch, youtu.be hoặc youtube.com/shorts để mở video theo ID. Link sai có thông báo rõ ràng.
4. Màn hình xem có phát/dừng, tua, toàn màn hình, chọn chất lượng khả dụng, yêu thích và tiếp tục vị trí đã lưu.
5. Chuyển app hoặc khóa màn hình vẫn tiếp tục phát. Có điều khiển trong thông báo/màn hình khóa và nút mở Picture-in-Picture (PiP).
6. Thư viện có lịch sử, yêu thích, bỏ yêu thích và xóa lịch sử. Dữ liệu tồn tại sau khi đóng/mở app.
7. Khi video kết thúc, dừng tại cuối video; không tự phát video khác trong bản đầu.

## Kiến trúc

### Giao diện

React Native quản lý giao diện chính. Kotlin Native Module kết nối qua `NativeModules` với khai báo TypeScript. Màn phát native dùng MediaController nối tới Media3 service và PlayerView; không tạo trình phát thứ hai khi chuyển màn hình hoặc PiP.

### Tìm kiếm

Module Android gọi Google bằng HTTP: search.list với part=snippet, type=video, maxResults=20; videos.list theo lô bổ sung contentDetails.duration. Truy vấn tiếp dùng nextPageToken. Kết quả chỉ được hiển thị nếu vẫn thuộc truy vấn hiện tại; không gửi nhiều yêu cầu tải cùng trang đồng thời.

Gradle đọc YOUTUBE_API_KEY từ .env và đưa vào BuildConfig. Thiếu key làm tìm kiếm báo hướng dẫn cấu hình; dán link vẫn hoạt động. Lỗi key, quota, mạng được phân biệt; không tự đổi nhà cung cấp tìm kiếm.

### Nguồn phát

NewPipe Extractor lấy metadata và stream theo video ID, chạy ngoài UI thread. Cấu hình downloader, desugaring và quy tắc shrinker theo phiên bản thư viện đã khóa. Không dùng Google Data API để lấy URL phát.

Media3 ExoPlayer duy nhất thuộc MediaSessionService. Dùng MediaController kết nối giao diện/service; module JS không sở hữu vòng đời player. Ghép nguồn hình và tiếng khi cần. Chọn chất lượng mặc định cao nhất không quá 720p; nếu không có thì chọn chất lượng thấp nhất khả dụng. Danh sách chọn chất lượng chỉ hiển thị nguồn có thể phát.

Đổi chất lượng giữ vị trí và trạng thái phát/dừng. Nguồn hết hạn được trích xuất lại và khôi phục một lần; nếu vẫn lỗi thì cho người dùng thử lại. Không tự lặp vô hạn. Khi app bị hệ điều hành kết thúc, lần mở lại cho tiếp tục từ vị trí đã lưu, không tự phát bất ngờ.

Khai báo foreground service loại mediaPlayback, quyền liên quan, media notification và PiP. Xử lý audio focus; tạm dừng khi rút tai nghe. Service duy trì phát khi Activity ở nền; không phụ thuộc JS timer.

### Lưu trữ

Room lưu video ID, tiêu đề, kênh, thumbnail, thời lượng, vị trí, thời điểm xem và trạng thái yêu thích. Service lưu vị trí mỗi 5 giây và khi pause/stop/chuyển video. Không lưu URL nguồn phát lâu dài. Xóa lịch sử không xóa yêu thích. Yêu thích không được trùng video ID.

## Hợp đồng TypeScript–native

- VideoSummary: id, title, channelTitle, thumbnailUrl, durationMs (nullable).
- SearchPage: items: VideoSummary[], nextPageToken: string | null.
- VideoDetails: video: VideoSummary, qualities: {id, label, height}[]; URL phát giữ trong native.
- searchVideos(query, pageToken?): Promise<SearchPage>.
- getVideo(videoId): Promise<VideoDetails>.
- load(videoId, startPositionMs?), play(), pause(), seekTo(positionMs), setQuality(qualityId), stop(), enterPip().
- Sự kiện playbackState: videoId, status, positionMs, durationMs, qualityId và error nếu có. status thuộc idle/loading/playing/paused/ended/error.
- getHistory(), getFavorites(), setFavorite(videoId, enabled), clearHistory(). Metadata đã lấy được dùng khi lưu.
- Lỗi có code và message tiếng Việt: CONFIG_MISSING, API_KEY_INVALID, QUOTA_EXCEEDED, NETWORK, INVALID_URL, VIDEO_UNAVAILABLE, EXTRACTION_FAILED, PLAYBACK_FAILED.

## Key và cấu hình

.env chứa YOUTUBE_API_KEY, bị loại khỏi Git. .env.example chỉ chứa tên biến với giá trị trống. Không đưa key vào tài liệu, log hoặc thông báo lỗi. Key được đóng gói lúc build APK; đổi .env yêu cầu build lại APK. Không coi .env là cơ chế giữ bí mật bên trong APK.

## Nghiệm thu

- Tìm kiếm tiếng Việt, phân trang, kết quả rỗng và chuyển nhanh truy vấn không hiển thị kết quả cũ.
- Key thiếu/sai, hết quota, mất mạng đều có thông báo và hành động phù hợp.
- Video công khai phát có hình/tiếng đồng bộ; tua và đổi chất lượng giữ vị trí.
- Toàn màn hình, xoay thiết bị, PiP, nghe nền, thông báo, khóa màn hình, audio focus và ngắt tai nghe hoạt động nhất quán.
- Lịch sử/yêu thích/vị trí xem tồn tại sau khi mở lại ứng dụng.
- APK chạy độc lập không cần Metro; bàn giao mã nguồn, APK, hướng dẫn cài/thay key và ghi rõ phần chưa kiểm chứng.

## Bằng chứng và môi trường hiện tại

- Google search.list đã trả HTTP 200 với key được cung cấp; chỉ chứng minh tìm kiếm hoạt động tại thời điểm kiểm tra.
- ADB đã thấy emulator-5554, Android 17/API 37, sys.boot_completed=1.
- Kho mã ban đầu trống; chưa kiểm chứng trích xuất hay phát video.

## Tài liệu tham chiếu

- https://reactnative.dev/docs/turbo-native-modules-introduction
- https://reactnative.dev/docs/fabric-native-components-introduction
- https://github.com/TeamNewPipe/NewPipeExtractor
- https://developer.android.com/media/media3/session/background-playback
- https://developer.android.com/develop/ui/views/picture-in-picture
- https://developers.google.com/youtube/v3/docs/search/list

## Quy ước triển khai

Hoàn thành và kiểm tra từng chức năng rồi commit riêng; không commit key. Khóa phiên bản dependency, giữ thông tin giấy phép của thư viện sử dụng. Nếu kiểm tra nguồn phát thất bại, giải quyết nguyên nhân trước khi hoàn thiện toàn bộ giao diện.
