# TubeLite — Mở video tức thì và mini-player

Ngày: 2026-10-20

Trạng thái: Đã duyệt, triển khai và kiểm tra tự động hoàn tất.

## 1. Mục tiêu

Khi người dùng chọn video, màn phát cần xuất hiện ngay trong lúc app lấy stream. Khi đang xem, vuốt xuống để thu video thành mini-player và đưa trang chủ ra sử dụng, trong khi video tiếp tục phát. Chạm mini-player mở lại màn phát.

Thành công nghĩa là thời gian lấy stream không chặn điều hướng; mini-player và màn phát luôn điều khiển cùng phiên phát do `PlaybackService` sở hữu; thao tác qua lại không làm mất vị trí hoặc tạo player thứ hai.

## 2. Hiện trạng và phạm vi

- `NativeVideoExtractorModule.openVideo()` hiện resolve NewPipe và đọc lịch sử trước khi khởi chạy `PlaybackActivity`, nên độ trễ mạng chặn việc mở màn.
- `PlaybackActivity` là Android Activity riêng, gắn `PlayerView` với `MediaController` của `PlaybackService`.
- Trang chủ và tab Thư viện nằm trong React Native `App.tsx`; hiện chưa có mini-player.
- Thay đổi áp dụng khi mở video từ trang chủ, tìm kiếm, thư viện và link YouTube.
- Giữ NewPipe, một ExoPlayer/MediaSession trong `PlaybackService`, lịch sử, yêu thích, autoplay, PiP và phát nền.
- Không thay đổi cách tìm kiếm, gợi ý, nội dung trang chủ, hay nguồn stream.

## 3. Trải nghiệm người dùng

### Mở video

1. Chọn video mở màn phát ngay, không chờ trích xuất stream hoặc đọc lịch sử hoàn tất.
2. Khung video hiện trạng thái **Đang tải video…**; không thể hiện nhầm là đang tạm dừng.
3. Khi stream sẵn sàng, video phát và metadata/lịch sử được cập nhật như hiện tại.
4. Nếu lấy stream thất bại, màn phát vẫn mở với thông báo dễ hiểu và nút **Thử lại**.
5. Nếu chọn video khác trong khi đang tải, chỉ kết quả của lựa chọn mới nhất được áp dụng.

### Thu nhỏ và mở lại

1. Vuốt xuống bắt đầu từ vùng video trên màn phát để thu màn phát thành mini-player ở đáy giao diện TubeLite; trang chủ được hiển thị.
2. Video tiếp tục phát trong mini-player. Mini-player hiển thị hình video, tiêu đề ngắn, nút phát/tạm dừng và nút đóng.
3. Chạm vào vùng mini-player ngoài nút điều khiển sẽ mở lại màn phát toàn màn hình tại vị trí hiện tại.
4. Nút đóng dừng phiên phát và ẩn mini-player. Vuốt ngang hoặc thao tác nút điều khiển của video không kích hoạt thu nhỏ.
5. Vuốt xuống khi video đang tải vẫn thu nhỏ được; trạng thái tải/lỗi và nút thử lại còn nguyên trong mini-player hoặc khi mở lại màn phát.
6. Mini-player chỉ hiển thị khi app TubeLite đang ở foreground. PiP hệ thống và phát nền khi rời app tiếp tục theo hành vi hiện tại.

## 4. Thiết kế kỹ thuật

### Điều phối playback

- `openVideo(videoId, qualityId)` xác thực đầu vào, yêu cầu mở màn phát và gửi lựa chọn đến service mà không chờ NewPipe hoặc Room.
- Promise chỉ báo thao tác mở/yêu cầu đã được tiếp nhận. Lỗi khởi chạy được trả về ngay; lỗi trích xuất được hiển thị trong UI playback.
- `PlaybackService` công bố snapshot có video ID và trạng thái `loading` trước khi resolve. Lịch sử/vị trí đọc và ghi trên executor nền.
- Mỗi lựa chọn/retry có generation riêng. Kết quả cũ không được thay video, metadata, lỗi hoặc trạng thái của lựa chọn mới.
- Giữ một ExoPlayer và một MediaSession. Không tạo player mới khi đổi giữa màn phát và mini-player.

### Điều hướng và bề mặt video

- Màn phát toàn màn hình và mini-player gắn vào cùng phiên `PlaybackService`.
- Khi thu nhỏ, `PlaybackActivity` kết thúc/ẩn và app trở lại `MainActivity`; React Native chuyển về tab Trang chủ.
- MainActivity hiển thị mini-player nhỏ gọn trên giao diện React Native, neo phía trên thanh điều hướng/tab đáy. Vùng ngoài mini-player tiếp tục nhận tương tác của trang chủ.
- Mở lại mini-player đưa `PlaybackActivity` lên trước, kết nối lại cùng session và khôi phục trạng thái/vị trí từ service.
- Mini-player phải tách việc hiển thị khỏi điều phối phát để Activity có thể được tạo lại mà không làm mất phiên.
- Vuốt xuống chỉ nhận gesture dọc bắt đầu ở vùng video; có ngưỡng khoảng cách/vận tốc để tránh xung đột với tap, kéo thanh thời gian, fullscreen và vuốt ngang của app.

### Trạng thái, lỗi và dữ liệu

- Service snapshot là nguồn sự thật cho video ID, tiêu đề, trạng thái phát, vị trí và lỗi.
- Trạng thái `loading`, `playing`, `paused`, `error`, `ended` được thể hiện nhất quán giữa màn phát và mini-player.
- Nút thử lại tạo request generation mới; không tự retry vô hạn.
- Đọc vị trí đã lưu trên executor. Video chưa xem hết tiếp tục từ vị trí đã lưu; video gần cuối bắt đầu lại theo quy tắc hiện tại.
- Chỉ ghi metadata/lịch sử thực sau khi resolve thành công; không tạo dữ liệu giả để lấp UI tải.
- Vuốt thu nhỏ, mở lại, Back hoặc tái tạo Activity không tạo bản ghi lịch sử mới và không đổi vị trí ngoài tiến trình playback tự nhiên.

## 5. Nghiệm thu

- Với resolver bị trì hoãn, màn phát và nhãn tải xuất hiện trước khi resolver hoàn tất.
- Khi resolve thành công, cùng player bắt đầu phát; không có player hoặc MediaSession thứ hai.
- Lỗi resolve hiển thị nút thử lại; thử lại thành công phát đúng lựa chọn hiện tại.
- Chọn A rồi B khi A đang tải không cho kết quả A ghi đè B.
- Vuốt xuống từ vùng video đưa app về Trang chủ và hiện mini-player; video vẫn chạy.
- Mini-player play/pause và đóng hoạt động đúng; chạm vùng video mở lại màn phát đúng vị trí.
- Vuốt ngang, kéo seek bar, thao tác điều khiển và xoay màn hình không vô tình thu nhỏ.
- Mini-player còn đúng sau khi Activity phát bị tái tạo; PiP, phát nền, autoplay, yêu thích, lịch sử và tiếp tục vị trí không hồi quy.
- Unit tests bao phủ state/generation/gesture policy; instrumentation tests bao phủ điều hướng, hiển thị mini-player và gắn lại cùng playback session.
- Chạy toàn bộ Jest, lint, TypeScript, Android unit/instrumentation tests và build debug/release. Kiểm tra thủ công trên emulator cho gesture, điều hướng và phát liên tục.

## 6. Rủi ro và quyết định

- Thách thức chính là đưa hình video từ Activity phát sang mini-player trong MainActivity trong khi chỉ có một ExoPlayer. Việc chuyển bề mặt phải không giải phóng service/player; cần kiểm thử vòng đời và thứ tự tháo/gắn `PlayerView`.
- Điều hướng tab Trang chủ từ native cần một hợp đồng sự kiện/bridge rõ ràng, tránh cập nhật React state sau khi component đã bị hủy.
- Mini-player không được che vùng tương tác chính của trang chủ hoặc thanh tab; kích thước và vùng chạm phải được kiểm tra trên nhiều kích thước emulator.
- Nếu Android hạn chế khởi chạy Activity/service hoặc service không thể nhận yêu cầu, bridge trả lỗi hữu hạn để UI không mắc ở loading.
- Không cam kết thời gian bắt đầu phát cố định; mạng và trích xuất YouTube vẫn quyết định thời gian này.
