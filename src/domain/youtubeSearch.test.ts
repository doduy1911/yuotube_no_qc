import {
  mapSearchPage,
  getSearchErrorMessage,
  mergeUniqueVideos,
  parseIso8601Duration,
} from './youtubeSearch';

describe('YouTube search response mapping', () => {
  it('maps video metadata, duration and pagination token', () => {
    expect(
      mapSearchPage({
        nextPageToken: 'page-2',
        items: [
          {
            videoId: 'dQw4w9WgXcQ',
            title: 'Sample video',
            channelTitle: 'Sample channel',
            thumbnailUrl: 'https://img.example/thumb.jpg',
            publishedAt: '2026-01-02T03:04:05Z',
            duration: 'PT1H2M3S',
          },
        ],
      }),
    ).toEqual({
      nextPageToken: 'page-2',
      items: [
        {
          videoId: 'dQw4w9WgXcQ',
          title: 'Sample video',
          channelTitle: 'Sample channel',
          thumbnailUrl: 'https://img.example/thumb.jpg',
          publishedAt: '2026-01-02T03:04:05Z',
          durationMs: 3_723_000,
        },
      ],
    });
  });

  it('parses ISO durations including days and missing fields', () => {
    expect(parseIso8601Duration('P1DT2H3M4S')).toBe(93_784_000);
    expect(parseIso8601Duration('PT15M')).toBe(900_000);
    expect(parseIso8601Duration('LIVE')).toBeNull();
  });

  it('drops incomplete items rather than producing unusable results', () => {
    expect(mapSearchPage({items: [{title: 'Missing id'}]}).items).toEqual([]);
  });

  it('deduplicates videos when adjacent pages overlap', () => {
    const video = {
      videoId: 'dQw4w9WgXcQ',
      title: 'Sample video',
      channelTitle: 'Sample channel',
      thumbnailUrl: null,
      publishedAt: null,
      durationMs: null,
    };
    expect(mergeUniqueVideos([video], [video, {...video, videoId: 'abcdefghijk'}])).toEqual([
      video,
      {...video, videoId: 'abcdefghijk'},
    ]);
  });

  it('shows actionable messages for missing key and exhausted quota', () => {
    expect(getSearchErrorMessage({code: 'API_KEY_MISSING'})).toMatch(/API key/);
    expect(getSearchErrorMessage({code: 'API_QUOTA_EXCEEDED'})).toMatch(/hạn mức/);
    expect(getSearchErrorMessage({code: 'API_NETWORK_ERROR'})).toMatch(/mạng/);
  });
});
