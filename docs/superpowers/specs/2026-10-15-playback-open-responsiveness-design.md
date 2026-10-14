# TubeLite — Đặc tả mở màn hình phát tức thì

Ngày: 2026-10-15

Trạng thái: Spec để người dùng review; chưa triển khai.

## 1. Mục tiêu

Khi người dùng chạm một video, màn hình phát phải xuất hiện ngay và phản hồi rõ ràng trong lúc nguồn phát được trích xuất. Hiện tại `NativeVideoExtractorModule.openVideo()` chờ `NewPipeVideoRepository.resolveVideo()` hoàn tất rồi mới khởi chạy `PlaybackActivity`, nên thời gian mạng làm chậm phản hồi của thao tác chạm.

Thành công nghĩa là màn hình phát mở trước khi yêu cầu mạng lấy stream hoàn thành; video bắt đầu phát khi stream sẵn sàng. Thao tác này không được tạo player thứ hai, làm mất vị trí tiếp tục, hoặc khiến kết quả cũ thay bài người dùng vừa chọn.

## 2. Phạm vi

- Áp dụng cho mở video từ trang chủ, kết quả tìm kiếm, thư viện và link YouTube.
- Giữ nguyên nguồn phát NewPipe, `PlaybackService` làm chủ ExoPlayer và cách lưu lịch sử/yêu thích.
- Không thay đổi giao diện trang chủ, thuật toán gợi ý, API tìm kiếm hoặc chính sách chất lượng.
- Không hứa thời gian bắt đầu phát cố định: mạng và khả năng trích xuất của YouTube vẫn quyết định thời gian này.

## 3. Trải nghiệm và trạng thái

1. Khi chọn video, mở `PlaybackActivity` ngay lập tức.
2. Màn hình hiển thị trạng thái **Đang tải video…**, giữ khung phát màu đen và không hiển thị trạng thái tạm dừng gây hiểu nhầm. Metadata có thể hiện khi service nhận được.
3. Service trích xuất stream ở nền. Khi có stream hợp lệ, phát video và cập nhật tiêu đề, kênh, lịch sử và trạng thái phát như hiện tại.
4. Nếu trích xuất thất bại, giữ màn hình mở, hiển thị lỗi dễ hiểu cùng thao tác **Thử lại**. Không đóng màn hình và không báo lỗi bằng hộp thoại ở React Native sau khi màn hình phát đã mở.
5. Chạm video khác trong lúc đang tải sẽ thay yêu cầu hiện tại. Chỉ kết quả của lựa chọn mới nhất được phép bắt đầu phát.
6. Back trong khi tải hoạt động như Back thông thường; khi Activity đóng, service vẫn tuân theo vòng đời phát nền hiện tại. Không tự dừng phát nền.

## 4. Thiết kế kỹ thuật

### Điều phối mở

- `NativeVideoExtractorModule.openVideo()` xác thực ID, yêu cầu khởi chạy `PlaybackActivity` và gửi yêu cầu chọn video đến `PlaybackService` mà không chờ NewPipe.
- Service được khởi chạy theo đúng quy tắc foreground service hiện có. Ý định mở Activity và chọn bài phải mang cùng video ID; xử lý lặp do kết nối MediaController không được tạo lượt phát/trích xuất thứ hai.
- Promise của bridge hoàn tất sau khi yêu cầu mở đã được tiếp nhận và Activity được khởi chạy, không chờ stream. React Native chỉ hiển thị lỗi nếu không thể khởi tạo yêu cầu hoặc Activity.
- Service tiếp tục dùng executor/resolver nền để lấy metadata và stream. Không thực hiện trích xuất hoặc truy vấn Room đồng bộ trên main thread.

### Trạng thái tải và kết quả

- Service công bố video ID đang được chọn cùng trạng thái `loading` trước khi bắt đầu resolve, để Activity mới kết nối có thể dựng đúng trạng thái ngay từ snapshot đầu tiên.
- Trạng thái tải thuộc service, không phải state cục bộ của Activity; tái tạo Activity, xoay màn hình, PiP hoặc kết nối lại controller không làm mất trạng thái.
- Khi resolve thành công, chỉ áp dụng kết quả nếu mã thế hệ yêu cầu và video ID vẫn khớp lựa chọn hiện hành. Sau đó chuyển sang các trạng thái phát hiện có.
- Khi resolve lỗi, service công bố trạng thái `error` và thông điệp có thể thử lại. Lệnh thử lại tạo generation mới. Kết quả lỗi cũ không được ghi đè lần thử mới.
- Trong lúc tải, thông tin gợi ý cũ không được gắn với video mới; khu vực gợi ý thể hiện trạng thái đang chờ hoặc chưa có gợi ý.

### Tiếp tục vị trí và lịch sử

- Đọc vị trí đã lưu trên executor nền. Nếu video chưa xem hết (hoặc thời lượng chưa biết), tiếp tục từ vị trí đã lưu; video đã xem gần hết bắt đầu lại từ 0 theo quy tắc V1.
- Ghi metadata/lịch sử sau khi trích xuất thành công như hiện tại. Không ghi metadata giả chỉ để hiển thị loading.
- Việc tải lại hoặc lựa chọn video khác phải giữ đúng thứ tự cập nhật lịch sử; tác vụ của lựa chọn cũ không được ghi vị trí cho video mới.

## 5. Hợp đồng bridge và trạng thái nội bộ

- Giữ phương thức `openVideo(videoId, qualityId)` để không cần thay call-site TypeScript; thay đổi ý nghĩa Promise thành “đã khởi chạy màn hình và gửi yêu cầu”, thay vì “đã resolve stream”. Nếu cần trả kết quả, `true` chỉ có nghĩa yêu cầu được tiếp nhận.
- Dùng trạng thái playback hiện có `loading` và `error`; bổ sung thông tin lỗi có thể hiển thị và hành động retry trong snapshot/service command nếu hợp đồng hiện tại chưa biểu đạt được.
- Không gửi URL stream qua JavaScript. MediaSession/MediaController tiếp tục là kênh trạng thái giữa service và Activity.

## 6. Nghiệm thu

- Với cache mạng lạnh và độ trễ resolve có thể quan sát, `PlaybackActivity` xuất hiện trước khi resolve kết thúc và hiển thị **Đang tải video…**.
- Khi resolve xong, video được phát bằng ExoPlayer hiện có; không tạo MediaSession/player thứ hai.
- Khi resolve lỗi hoặc mất mạng, màn hình có lỗi và thử lại; thử lại thành công sẽ phát video.
- Chọn A rồi nhanh chóng chọn B: kết quả A không được thay B hoặc thay metadata/trạng thái B.
- Mở video đã xem tiếp tục vị trí; video gần cuối bắt đầu từ đầu. Lịch sử và yêu thích vẫn hoạt động.
- Back, xoay, PiP và phát nền không làm mất/yêu cầu lại stream nếu không cần thiết.
- Kiểm tra bằng unit tests cho điều phối generation/trạng thái, kiểm thử bridge/service phù hợp, build debug/release và chạy APK trên emulator. Ghi nhận riêng thời điểm Activity mở và thời điểm phát bắt đầu; không coi hai mốc này là một.

## 7. Rủi ro và quyết định triển khai

- Android có giới hạn khởi chạy Activity và foreground service. Thứ tự khởi chạy phải tuân thủ API/target hiện tại; lỗi khởi tạo cần được trả về bridge thay vì để màn hình loading vô hạn.
- `PlaybackActivity` hiện kết nối MediaController bất đồng bộ. Snapshot đầu tiên phải chứa pending video ID/trạng thái loading, kể cả khi Activity kết nối trước khi stream sẵn sàng.
- Retry cần giới hạn ở thao tác người dùng; không tự resolve lặp vô hạn.
- Không thêm thời gian chờ giả hoặc spinner tối thiểu. Màn hình phản hồi nhanh nhất có thể; thời gian tải thật vẫn được hiển thị.
