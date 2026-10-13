export type VideoSummary = {
  videoId: string;
  title: string;
  channelTitle: string;
  thumbnailUrl: string | null;
  publishedAt: string | null;
  durationMs: number | null;
};

export type SearchPage = {
  items: VideoSummary[];
  nextPageToken: string | null;
};

type NativeSearchItem = {
  videoId?: unknown;
  title?: unknown;
  channelTitle?: unknown;
  thumbnailUrl?: unknown;
  publishedAt?: unknown;
  duration?: unknown;
};

export function parseIso8601Duration(value: unknown): number | null {
  if (typeof value !== 'string') {
    return null;
  }

  const match = value.match(
    /^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$/,
  );
  if (!match || !match.slice(1).some(Boolean)) {
    return null;
  }

  const [, days = '0', hours = '0', minutes = '0', seconds = '0'] = match;
  return (
    (((Number(days) * 24 + Number(hours)) * 60 + Number(minutes)) * 60 +
      Number(seconds)) *
    1000
  );
}

export function mapSearchPage(payload: unknown): SearchPage {
  if (!payload || typeof payload !== 'object') {
    return {items: [], nextPageToken: null};
  }

  const result = payload as {items?: unknown; nextPageToken?: unknown};
  const items = Array.isArray(result.items)
    ? result.items.flatMap((value: NativeSearchItem) => {
        if (
          typeof value.videoId !== 'string' ||
          !/^[A-Za-z0-9_-]{11}$/.test(value.videoId)
        ) {
          return [];
        }

        return [
          {
            videoId: value.videoId,
            title: typeof value.title === 'string' ? value.title : '',
            channelTitle:
              typeof value.channelTitle === 'string' ? value.channelTitle : '',
            thumbnailUrl:
              typeof value.thumbnailUrl === 'string'
                ? value.thumbnailUrl
                : null,
            publishedAt:
              typeof value.publishedAt === 'string' ? value.publishedAt : null,
            durationMs: parseIso8601Duration(value.duration),
          },
        ];
      })
    : [];

  return {
    items,
    nextPageToken:
      typeof result.nextPageToken === 'string' ? result.nextPageToken : null,
  };
}

export function mergeUniqueVideos(
  current: VideoSummary[],
  incoming: VideoSummary[],
): VideoSummary[] {
  const seen = new Set(current.map(video => video.videoId));
  return [
    ...current,
    ...incoming.filter(video => {
      if (seen.has(video.videoId)) return false;
      seen.add(video.videoId);
      return true;
    }),
  ];
}

export function getSearchErrorMessage(error: unknown): string {
  const code = (error as {code?: string} | null)?.code;
  switch (code) {
    case 'API_KEY_MISSING':
      return 'Chưa cấu hình YouTube API key. Bạn vẫn có thể dán link video để mở.';
    case 'API_KEY_INVALID':
    case 'API_NOT_ENABLED':
      return 'YouTube API key chưa hợp lệ hoặc YouTube Data API chưa được bật.';
    case 'API_QUOTA_EXCEEDED':
      return 'Đã hết hạn mức tìm kiếm YouTube API. Hãy thử lại sau.';
    case 'API_NETWORK_ERROR':
    case 'API_UNAVAILABLE':
      return 'Không kết nối được YouTube. Kiểm tra mạng rồi thử lại.';
    default:
      return 'Tìm kiếm không thành công. Hãy thử lại hoặc dán link video.';
  }
}
