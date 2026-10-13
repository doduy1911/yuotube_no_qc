import {openYouTubeLink} from './youtubePlayback';

describe('openYouTubeLink', () => {
  it('opens the parsed video id with the default quality', async () => {
    const openVideo = jest.fn().mockResolvedValue(true);

    await expect(
      openYouTubeLink('https://youtu.be/dQw4w9WgXcQ', openVideo),
    ).resolves.toBe('dQw4w9WgXcQ');

    expect(openVideo).toHaveBeenCalledWith('dQw4w9WgXcQ', '');
  });

  it('does not call the native player for an invalid link', async () => {
    const openVideo = jest.fn().mockResolvedValue(true);

    await expect(openYouTubeLink('https://example.com/video', openVideo)).rejects.toThrow(
      'INVALID_URL',
    );
    expect(openVideo).not.toHaveBeenCalled();
  });
});
