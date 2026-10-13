const YOUTUBE_HOSTS = new Set([
  'youtube.com',
  'www.youtube.com',
  'm.youtube.com',
  'music.youtube.com',
  'youtu.be',
]);

const VIDEO_ID_PATTERN = /^[A-Za-z0-9_-]{11}$/;

export function parseYouTubeVideoId(input: string): string | null {
  const match = input.trim().match(/^https?:\/\/([^/?#]+)(\/[^?#]*)?(?:\?([^#]*))?/i);
  if (!match) {
    return null;
  }

  const host = match[1].toLowerCase();
  const path = match[2] ?? '/';
  const query = match[3] ?? '';
  if (!YOUTUBE_HOSTS.has(host)) {
    return null;
  }

  let videoId: string | null = null;

  if (host === 'youtu.be') {
    videoId = path.split('/').filter(Boolean)[0] ?? null;
  } else if (path === '/watch') {
    videoId = query.match(/(?:^|&)v=([^&]*)/)?.[1] ?? null;
  } else if (path.startsWith('/shorts/')) {
    videoId = path.split('/')[2] ?? null;
  }

  return videoId && VIDEO_ID_PATTERN.test(videoId) ? videoId : null;
}
