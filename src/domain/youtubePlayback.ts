import {parseYouTubeVideoId} from './youtubeUrl';

type OpenVideo = (videoId: string, qualityId: string) => Promise<boolean>;

export async function openYouTubeLink(
  input: string,
  openVideo: OpenVideo,
): Promise<string> {
  const videoId = parseYouTubeVideoId(input);
  if (!videoId) {
    throw new Error('INVALID_URL');
  }

  await openVideo(videoId, '');
  return videoId;
}
