import type {VideoSummary} from './youtubeSearch';

export type TrendingSectionId = 'vn' | 'international';

export type TrendingSection = {
  section: TrendingSectionId;
  items: VideoSummary[];
  regions: string[];
  updatedAt: number;
  stale: boolean;
};

export function mapTrendingSection(value: unknown): TrendingSection {
  const raw = (value && typeof value === 'object' ? value : {}) as Record<string, unknown>;
  const items = Array.isArray(raw.items) ? raw.items.flatMap(item => {
    if (!item || typeof item !== 'object') return [];
    const row = item as Record<string, unknown>;
    if (typeof row.videoId !== 'string' || !/^[\w-]{11}$/.test(row.videoId)) return [];
    return [{
      videoId: row.videoId,
      title: typeof row.title === 'string' ? row.title : '',
      channelTitle: typeof row.channelTitle === 'string' ? row.channelTitle : '',
      thumbnailUrl: typeof row.thumbnailUrl === 'string' ? row.thumbnailUrl : null,
      publishedAt: typeof row.publishedAt === 'string' ? row.publishedAt : null,
      durationMs: typeof row.durationMs === 'number' ? row.durationMs : null,
    }];
  }) : [];
  return {
    section: raw.section === 'international' ? 'international' : 'vn',
    items,
    regions: Array.isArray(raw.regions) ? raw.regions.filter((region): region is string => typeof region === 'string') : [],
    updatedAt: typeof raw.updatedAt === 'number' ? raw.updatedAt : 0,
    stale: raw.stale === true,
  };
}
