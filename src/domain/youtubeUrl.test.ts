import {parseYouTubeVideoId} from './youtubeUrl';

describe('parseYouTubeVideoId', () => {
  it('reads the video id from a watch URL and ignores tracking parameters', () => {
    expect(
      parseYouTubeVideoId(
        'https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=share',
      ),
    ).toBe('dQw4w9WgXcQ');
  });

  it('reads the video id from a short YouTube URL', () => {
    expect(parseYouTubeVideoId('https://youtu.be/dQw4w9WgXcQ?t=30')).toBe(
      'dQw4w9WgXcQ',
    );
  });

  it('reads the video id from a Shorts URL', () => {
    expect(
      parseYouTubeVideoId('https://youtube.com/shorts/dQw4w9WgXcQ'),
    ).toBe('dQw4w9WgXcQ');
  });

  it('rejects URLs from hosts that only imitate YouTube', () => {
    expect(
      parseYouTubeVideoId('https://youtube.com.attacker.example/watch?v=dQw4w9WgXcQ'),
    ).toBeNull();
  });

  it('rejects unsupported routes and malformed video ids', () => {
    expect(parseYouTubeVideoId('https://www.youtube.com/channel/abc')).toBeNull();
    expect(parseYouTubeVideoId('https://youtu.be/short')).toBeNull();
    expect(parseYouTubeVideoId('not a URL')).toBeNull();
  });
});
