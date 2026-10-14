import {mapUpdateCheckResponse, type UpdateCheckResult} from '../../domain/apkUpdate';

export type UpdateDeviceInfo = {
  packageName: string;
  currentVersionCode: number;
  sdkInt: number;
  supportedAbis: string[];
  serverUrl: string;
};

export interface UpdateNativeApi {
  getDeviceInfo(): Promise<string>;
}

type FetchResponse = {ok: boolean; status: number; json(): Promise<unknown>};
type Fetcher = (url: string, init: RequestInit) => Promise<FetchResponse>;

export function createUpdateClient(native: UpdateNativeApi, fetcher: Fetcher) {
  let pendingCheck: Promise<UpdateCheckResult> | null = null;

  return {
    check(): Promise<UpdateCheckResult> {
      if (pendingCheck) return pendingCheck;
      const check = (async () => {
        const device = JSON.parse(await native.getDeviceInfo()) as UpdateDeviceInfo;
        const baseUrl = device.serverUrl.replace(/\/$/, '');
        const response = await fetcher(`${baseUrl}/api/v1/updates/check`, {
          method: 'POST',
          headers: {'Content-Type': 'application/json'},
          body: JSON.stringify({
            packageName: device.packageName,
            currentVersionCode: device.currentVersionCode,
            sdkInt: device.sdkInt,
            supportedAbis: device.supportedAbis,
          }),
        });
        if (!response.ok) {
          if (response.status === 404) throw new Error('Ứng dụng này chưa được đăng ký trên máy chủ cập nhật.');
          throw new Error('Không kiểm tra được cập nhật. Vui lòng thử lại khi có mạng.');
        }
        return mapUpdateCheckResponse(await response.json(), device.packageName, device.currentVersionCode);
      })();
      pendingCheck = check;
      void check.finally(() => { if (pendingCheck === check) pendingCheck = null; }).catch(() => undefined);
      return check;
    },
  };
}
