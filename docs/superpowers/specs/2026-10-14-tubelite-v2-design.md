# TubeLite V2 — Đặc tả sản phẩm và kỹ thuật

Ngày: 2026-10-14

Trạng thái: Tài liệu V2 đã xuất theo yêu cầu; chưa triển khai mã V2.

## 1. Mục tiêu

Người dùng mở app là có nhạc để chọn, tìm bài nhanh hơn, vừa xem vừa khám phá bài khác và nghe liên tục. V2 kế thừa Android React Native, tiếng Việt, nền tối, lịch sử/yêu thích, phát nền và PiP của V1.

Yêu cầu đã thống nhất:

1. Trang chủ có nhạc thịnh hành Việt Nam và quốc tế.
2. Gợi ý từ khóa ngay khi nhập tìm kiếm.
3. Màn phát dọc có video phía trên, danh sách bài gợi ý bên dưới và nút toàn màn hình.
4. Tự phát bài tiếp theo bật mặc định, có công tắc bật/tắt.

Không bổ sung đăng nhập, tải nhạc, đồng bộ, playlist do người dùng tạo hoặc cá nhân hóa trang chủ trong V2.

## 2. Trang chủ thịnh hành

- Có hai mục: **Thịnh hành Việt Nam** và **Nhạc quốc tế**. Mỗi mục tối đa 20 video với ảnh, tiêu đề, kênh và thời lượng; chạm để phát.
- Việt Nam lấy nhạc phổ biến theo khu vực VN. Quốc tế mặc định tổng hợp Mỹ (US) và Anh (GB), xen kẽ theo thứ tự nguồn, loại trùng video ID rồi lấy tối đa 20 bài. Ghi chú nguồn “Mỹ & Anh”; không mô tả là bảng xếp hạng toàn cầu hoặc đảm bảo ngôn ngữ của bài.
- Dùng YouTube Data API `videos.list` với `chart=mostPopular`, `part=snippet,contentDetails`, `regionCode` tương ứng và danh mục Music. Xác minh danh mục được hỗ trợ trước khi chốt tham số trong triển khai; danh mục không khả dụng phải báo lỗi, không thay bằng danh sách mọi thể loại.
- Cache metadata trên máy trong một giờ. Mở app hiển thị cache ngay nếu có; quá hạn thì cập nhật nền. Kéo xuống để cập nhật ngay.
- Nếu mạng lỗi, có thể hiển thị cache cũ kèm thời điểm cập nhật và thông báo. Chưa có cache thì hiện lỗi cùng nút thử lại; không dùng dữ liệu giả.
- Hai mục tải độc lập; một mục lỗi không che mục còn lại. Quốc tế chỉ lấy được một nguồn thì hiển thị phần có sẵn và ghi đúng nguồn thực tế.
- Không tự phát nhạc khi mở app. Trang chủ vẫn có lối vào tìm kiếm và thư viện.

## 3. Gợi ý tìm kiếm

- Bắt đầu từ hai ký tự sau khi bỏ khoảng trắng đầu/cuối; chờ ngừng nhập 300 ms rồi gửi yêu cầu. Hiển thị tối đa tám cụm từ dưới ô nhập.
- Ví dụ “em của” có thể gợi ý “em của ngày hôm qua Sơn Tùng”; nội dung cụ thể phụ thuộc nguồn gợi ý, không hardcode ví dụ này.
- Dùng NewPipe `getSuggestionExtractor().suggestionList(query)`. Tìm kiếm danh sách video vẫn dùng Google như V1; không gọi `search.list` cho từng phím.
- Chạm gợi ý điền cụm từ và tìm ngay. Enter tìm theo nội dung hiện tại.
- Loại cụm từ trùng; chỉ nhận phản hồi thuộc truy vấn hiện tại. Xóa ô nhập hoặc gửi tìm kiếm đóng danh sách và vô hiệu hóa phản hồi đang chờ.
- Lỗi gợi ý không chặn tìm kiếm thủ công. Link YouTube hợp lệ không yêu cầu gợi ý, vẫn mở trực tiếp như V1.

## 4. Màn phát dọc và bài gợi ý

- Mặc định mở màn dọc: video trong khung 16:9 toàn chiều rộng phía trên; tiếp theo là tiêu đề, kênh, điều khiển yêu thích/tự phát; danh sách gợi ý cuộn trong phần còn lại.
- Giữ đúng tỷ lệ hình bên trong khung, kể cả video dọc. “Một nửa màn hình” được hiểu là bố cục có chỗ cho video và gợi ý, không ép video cao đúng 50% trên mọi thiết bị.
- Nút toàn màn hình mở chế độ chỉ xem video. Back từ toàn màn hình trở về màn dọc, không đổi bài/vị trí/trạng thái phát. Xoay ngang hiển thị chế độ video toàn màn hình; xoay về dọc phục hồi bố cục gợi ý.
- Ưu tiên video liên quan qua NewPipe `StreamInfo.getRelatedItems()`. Khi rỗng hoặc lỗi, dùng nhạc thịnh hành và nhãn **Khám phá thêm**; không gọi nguồn thay thế này là bài tương tự.
- Hiển thị tối đa 20 ứng viên; loại bài đang phát, video trùng, livestream, nội dung chưa phát hành và mục không phải video khi metadata cho biết. Không cam kết mọi video liên quan đều là nhạc nếu nguồn không cung cấp phân loại.
- Danh sách thịnh hành thay thế xen kẽ Việt Nam/quốc tế, loại trùng; có cache thì dùng cache trước. Không có ứng viên thì hiện trạng thái trống và thử lại.
- Chạm gợi ý đổi bài trong cùng màn hình, cập nhật metadata, yêu thích, chất lượng và gợi ý theo bài mới.
- Một ExoPlayer duy nhất thuộc service; Activity, toàn màn hình và PiP chỉ là các cách hiển thị phiên phát đó. PiP chỉ hiện video, ẩn danh sách gợi ý.

## 5. Tự phát bài tiếp theo

- Công tắc **Tự phát bài tiếp theo** bật mặc định lần đầu; ghi nhớ lựa chọn qua các lần mở app.
- Khi bài kết thúc tự nhiên, phát ứng viên hợp lệ đầu tiên theo thứ tự danh sách gợi ý của bài hiện tại, từ vị trí 0. Bài tự chuyển không tiếp tục vị trí xem cũ.
- Ưu tiên video liên quan; dùng thịnh hành khi không còn ứng viên liên quan dùng được. Giao diện phải phản ánh danh sách thực tế service đang dùng.
- Tránh lặp 50 bài gần nhất trong phiên nghe liên tục. Người dùng chủ động chọn lại bài vẫn được phép; phiên mới bắt đầu khi mở một bài thủ công từ trang chủ/tìm kiếm/thư viện. Không lưu phiên autoplay để tự khởi động lại sau khi app bị kết thúc.
- Pause, đổi chất lượng, xoay máy hoặc vào PiP không được kích hoạt chuyển bài. Tắt autoplay thì dừng ở cuối bài.
- Điều phối nằm trong service và hoạt động trong nền/khóa màn hình/PiP, không dùng JS timer.
- Mỗi lần tự chuyển chỉ thử tối đa ba ứng viên không phát được. Mất mạng thì dừng, không quét tiếp danh sách; hết ứng viên thì dừng và báo trạng thái có thể thử lại thủ công.
- Một sự kiện kết thúc chỉ tạo một lần chuyển. Chọn bài khác, tắt autoplay hoặc dừng phiên trong lúc đang resolve sẽ vô hiệu hóa kết quả cũ.
- Khi đang tải gợi ý mà bài đã kết thúc, service chờ yêu cầu của đúng bài đó rồi quyết định một lần. Tải thất bại sử dụng fallback ở trên; hết ứng viên thì dừng.
- Nguồn hết hạn của bài hiện tại vẫn áp dụng giới hạn khôi phục một lần của V1; không coi sự cố này là bài kết thúc tự nhiên.

## 6. Kiến trúc và giao diện nội bộ

- React Native quản lý trang chủ, tìm kiếm và thư viện. Tách home/search khỏi `App.tsx` khi triển khai hai tính năng tương ứng.
- Mở rộng `PlaybackActivity` native thành màn phát dọc; tiếp tục kết nối MediaController tới `PlaybackService`. Không chuyển player sang React Native trong V2.
- Repository riêng quản lý trending và gợi ý tìm kiếm; NewPipe repository bổ sung video liên quan. Tái sử dụng metadata đã lấy được để tránh trích xuất cùng một video nhiều lần không cần thiết.
- Service sở hữu bài hiện tại, danh sách ứng viên, autoplay và mã thế hệ yêu cầu. UI nhận snapshot khi kết nối và cập nhật qua MediaSession; phản hồi cũ không được sửa phiên mới.
- Khi chuyển bài, chụp video ID/metadata/vị trí của bài cũ trước khi cập nhật trạng thái. Ghi Room trên executor với dữ liệu đã chụp, tránh tác vụ cũ ghi vị trí sang bài mới.
- Đổi chất lượng giữ cả vị trí và trạng thái play/pause. Bài phát xong lưu trạng thái vị trí nhất quán để mở thủ công lần sau bắt đầu lại; bài chưa kết thúc tiếp tục vị trí đã lưu.
- Cache trending lưu JSON metadata và thời điểm cập nhật trong bộ nhớ riêng của app; autoplay lưu SharedPreferences. Không cần thay schema Room cho riêng các tính năng V2; không xóa dữ liệu V1.
- Không lưu URL nguồn phát lâu dài. Giữ cấu hình key của V1, không đưa key vào tài liệu, log hoặc Git.

Các kiểu logic dùng chung:

```ts
type TrendingSectionId = 'vn' | 'international';
type TrendingSection = {
  section: TrendingSectionId;
  items: VideoSummary[];
  regions: string[]; // Nguồn thực tế dùng trong kết quả
  updatedAt: number; // Epoch milliseconds; quốc tế dùng thời điểm nguồn cũ nhất
  stale: boolean;
};
type RecommendationSet = {
  videoId: string;
  items: VideoSummary[];
  source: 'related' | 'trending';
};
```

`VideoSummary` tái sử dụng kiểu V1, khóa là `videoId`, thời lượng dùng milliseconds. Bridge hiện tại trả JSON string; wrapper TypeScript kiểm tra/ánh xạ sang kiểu logic, không truyền stream URL sang JS.

- `getTrendingMusic(section, forceRefresh): Promise<TrendingSection>`.
- `getSearchSuggestions(query): Promise<string[]>`.
- `getRecommendations(videoId): RecommendationSet` trong repository native, chạy ngoài UI thread.
- MediaSession bổ sung lệnh `GET_PLAYBACK_SCREEN_STATE`, `SELECT_VIDEO(videoId)`, `SET_AUTOPLAY(enabled)` và sự kiện `PLAYBACK_SCREEN_STATE_CHANGED`.
- Snapshot gồm video hiện tại hoặc null, status, positionMs, autoplayEnabled, recommendations, recommendationsLoading và error hoặc null. Status dùng idle/loading/playing/paused/ended/error; vị trí đọc thêm từ MediaController, không phát sự kiện mỗi frame.

## 7. Nghiệm thu

- Mở app có hai mục nhạc; kiểm tra cache, kéo làm mới, một nguồn lỗi và hết quota.
- Nhập tiếng Việt, xóa nhanh, đổi truy vấn nhanh, chọn gợi ý và dán link; không có phản hồi cũ ghi đè truy vấn mới.
- Mở bài thấy video và gợi ý; toàn màn hình/Back/xoay/PiP không khởi động lại bài hay tạo player thứ hai.
- Autoplay bật chuyển đúng một lần sang ứng viên hợp lệ; tắt dừng ở cuối; ghi nhớ công tắc; tránh lặp, giới hạn ba ứng viên lỗi và hủy resolve cũ.
- Chuyển bài khi app nền/khóa màn hình, cập nhật notification và metadata lịch sử đúng bài.
- Cài đè V1 giữ lịch sử/yêu thích; kiểm tra tiếp tục vị trí, phát lại bài kết thúc và trạng thái pause khi đổi chất lượng.
- Mỗi phần có test phù hợp, build, kiểm tra UI trên emulator và commit riêng. Ghi rõ kiểm tra thiết bị thật chưa thực hiện.
- APK standalone chạy khi không có Metro. Không tự phát hành V2 trước khi có yêu cầu phát hành.

## 8. Các mặc định kỹ thuật và nguồn tham chiếu

Các lựa chọn triển khai đã nêu trong bản kế hoạch chat, không phải yêu cầu riêng của người dùng: quốc tế dùng US+GB, khung 16:9, cache một giờ, gợi ý sau 300 ms/từ hai ký tự/tối đa tám dòng, ba ứng viên lỗi và tránh lặp 50 bài.

- YouTube videos.list: https://developers.google.com/youtube/v3/docs/videos/list
- Danh sách phổ biến theo khu vực: https://developers.google.com/youtube/v3/guides/implementation/videos
- `search.list.relatedToVideoId` đã ngừng hỗ trợ; không dùng cho gợi ý: https://developers.google.com/youtube/v3/revision_history
- NewPipe: https://github.com/TeamNewPipe/NewPipeExtractor
- Đã kiểm tra chữ ký `suggestionList(String)` và `StreamInfo.getRelatedItems()` trong JAR v0.26.5 của dự án. Đây là xác minh giao diện thư viện, chưa chứng minh nguồn gợi ý trực tuyến hoạt động trong V2.
