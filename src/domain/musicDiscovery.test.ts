import {mapRecommendationSet, mapTrendingSection} from './musicDiscovery';

test('maps trending metadata and ignores invalid items', () => {
  expect(mapTrendingSection({
    section: 'international', regions: ['US'], updatedAt: 123, stale: true,
    items: [
      {videoId: 'dQw4w9WgXcQ', title: 'Song', channelTitle: 'Artist', thumbnailUrl: 'https://img.test/a', durationMs: 192000},
      {videoId: 'bad', title: 'Invalid'},
    ],
  })).toEqual({
    section: 'international', regions: ['US'], updatedAt: 123, stale: true,
    items: [{videoId: 'dQw4w9WgXcQ', title: 'Song', channelTitle: 'Artist', thumbnailUrl: 'https://img.test/a', publishedAt: null, durationMs: 192000}],
  });
});

test('maps related recommendations and their fallback source without stream URLs', () => {
  expect(mapRecommendationSet({
    videoId: 'abcdefghijk', source: 'trending', error: null,
    items: [{videoId: 'dQw4w9WgXcQ', title: 'Song', channelTitle: 'Artist', thumbnailUrl: null, publishedAt: null, durationMs: 1000}],
  })).toEqual({
    videoId: 'abcdefghijk', source: 'trending', error: null,
    items: [{videoId: 'dQw4w9WgXcQ', title: 'Song', channelTitle: 'Artist', thumbnailUrl: null, publishedAt: null, durationMs: 1000}],
  });
});
