import React, {useCallback, useEffect, useRef, useState} from 'react';
import {AppState, Pressable, StyleSheet, Text, View} from 'react-native';
import NativeApkUpdater from '../../native/specs/NativeApkUpdater';
import {validateDownloadedUpdate, type UpdateCheckResult, type UpdateRelease} from '../../domain/apkUpdate';
import {createUpdateClient} from './updateClient';

type NativeState = {
  state: 'idle' | 'downloading' | 'paused' | 'readyToInstall' | 'waitingForUser' | 'installed' | 'error';
  downloadedBytes?: number;
  totalBytes?: number;
  message?: string;
  release?: UpdateRelease | null;
};

function formatSize(bytes: number) {
  return bytes >= 1024 ** 3 ? `${(bytes / 1024 ** 3).toFixed(1)} GB` : `${(bytes / 1024 ** 2).toFixed(1)} MB`;
}

export default function UpdateCenter() {
  const [state, setState] = useState<NativeState>({state: 'idle'});
  const [message, setMessage] = useState('');
  const [checking, setChecking] = useState(false);
  const inFlight = useRef(false);

  const refresh = useCallback(async () => {
    try { setState(JSON.parse(await NativeApkUpdater.getUpdateState()) as NativeState); }
    catch { /* The update action will show a useful error if the module is unavailable. */ }
  }, []);

  useEffect(() => {
    void refresh();
    const interval = state.state === 'downloading' || state.state === 'paused'
      ? setInterval(() => { void refresh(); }, 1200) : null;
    const subscription = AppState.addEventListener('change', next => { if (next === 'active') void refresh(); });
    return () => { if (interval !== null) clearInterval(interval); subscription.remove(); };
  }, [refresh, state.state]);

  async function check() {
    if (inFlight.current) return;
    inFlight.current = true;
    setChecking(true); setMessage('');
    try {
      const client = createUpdateClient(NativeApkUpdater, (url, init) => fetch(url, init));
      const result: UpdateCheckResult = await client.check();
      if (!result.updateAvailable) {
        setMessage(result.reason === 'UP_TO_DATE' ? 'TubeLite đang ở phiên bản mới nhất.' : 'Chưa có bản phù hợp với thiết bị này.');
        return;
      }
      setState(JSON.parse(await NativeApkUpdater.startDownload(JSON.stringify(result.release))) as NativeState);
      setMessage(`Đang tải TubeLite ${result.release.versionName} (${formatSize(result.release.sizeBytes)}).`);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Không kiểm tra được cập nhật.';
      setMessage(errorMessage);
      setState(current => ({...current, state: 'error', message: errorMessage}));
    } finally {
      inFlight.current = false;
      setChecking(false);
      await refresh();
    }
  }

  async function cancel() {
    try {
      setState(JSON.parse(await NativeApkUpdater.cancelDownload()) as NativeState);
      setMessage('Đã hủy tải bản cập nhật.');
    } catch { setMessage('Không hủy được lượt tải.'); }
  }

  async function install() {
    try {
      const release = state.release;
      if (!release) throw new Error('Không tìm thấy thông tin bản cập nhật.');
      const [rawArtifact, rawDevice] = await Promise.all([
        NativeApkUpdater.inspectDownloadedApk(), NativeApkUpdater.getDeviceInfo(),
      ]);
      const artifact = JSON.parse(rawArtifact);
      const device = JSON.parse(rawDevice);
      validateDownloadedUpdate(release, artifact, device.signingCertSha256);
      const result = await NativeApkUpdater.installDownloadedApk();
      if (result === 'permission_required') setMessage('Cho phép TubeLite cài ứng dụng trong phần cài đặt Android, rồi nhấn Cài đặt lại.');
      else setMessage('Đã mở trình cài đặt Android để xác nhận.');
      await refresh();
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Không cài được bản cập nhật.';
      setMessage(errorMessage);
      setState(current => ({...current, state: 'error', message: errorMessage}));
      await refresh();
    }
  }

  const downloading = state.state === 'downloading' || state.state === 'paused';
  const percent = state.totalBytes && state.totalBytes > 0
    ? Math.min(100, Math.floor((state.downloadedBytes ?? 0) / state.totalBytes * 100)) : 0;

  return (
    <View style={styles.container}>
      <View style={styles.row}>
        <View style={styles.copy}>
          <Text style={styles.heading}>Cập nhật ứng dụng</Text>
          <Text accessibilityRole="text" testID="update-status" style={styles.status}>
            {state.state === 'downloading' ? `Đang tải… ${percent}%` :
              state.state === 'paused' ? 'Tải đang tạm dừng.' :
              state.state === 'readyToInstall' ? `Đã tải xong${state.release ? ` · ${state.release.versionName}` : ''} · ${formatSize(state.totalBytes ?? 0)}` :
              state.state === 'waitingForUser' ? 'Đang chờ xác nhận cài đặt Android.' :
              state.state === 'installed' ? message || 'Đã cập nhật thành công.' :
            state.state === 'error' ? message || state.message || 'Cập nhật thất bại.' :
              message || 'Kiểm tra phiên bản mới của TubeLite.'}
          </Text>
          {state.release?.releaseNotes ? <Text style={styles.notes}>{state.release.releaseNotes}</Text> : null}
        </View>
        {downloading ? <Pressable accessibilityRole="button" onPress={() => void cancel()} style={styles.secondaryButton}><Text style={styles.secondaryText}>Hủy tải</Text></Pressable> :
          state.state === 'readyToInstall' || state.state === 'waitingForUser' ?
            <Pressable accessibilityRole="button" onPress={() => void install()} style={styles.primaryButton}><Text style={styles.primaryText}>Cài đặt</Text></Pressable> :
            <Pressable accessibilityRole="button" accessibilityLabel="Kiểm tra cập nhật" testID="check-update" onPress={check} disabled={checking} style={[styles.primaryButton, checking && styles.disabled]}>
              <Text style={styles.primaryText}>{checking ? 'Đang kiểm tra…' : 'Kiểm tra cập nhật'}</Text>
            </Pressable>}
      </View>
      {downloading && state.totalBytes && state.totalBytes > 0 ? <View style={styles.track}><View style={[styles.progress, {width: `${percent}%`}]} /></View> : null}
      {state.state === 'error' && message ? <Text accessibilityRole="alert" style={styles.error}>{message}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {marginHorizontal: 16, marginTop: 12, marginBottom: 4, padding: 14, borderRadius: 14, backgroundColor: '#191919', borderWidth: 1, borderColor: '#303030'},
  row: {flexDirection: 'row', alignItems: 'center', gap: 12},
  copy: {flex: 1},
  heading: {color: '#f5f5f5', fontSize: 14, fontWeight: '700'},
  status: {color: '#a9a9a9', fontSize: 12, marginTop: 4},
  notes: {color: '#8f8f8f', fontSize: 12, marginTop: 6},
  primaryButton: {backgroundColor: '#ff453a', paddingHorizontal: 14, paddingVertical: 10, borderRadius: 9},
  primaryText: {color: '#fff', fontSize: 12, fontWeight: '700'},
  secondaryButton: {paddingHorizontal: 12, paddingVertical: 9, borderRadius: 9, borderWidth: 1, borderColor: '#555'},
  secondaryText: {color: '#f5f5f5', fontSize: 12, fontWeight: '600'},
  disabled: {opacity: 0.55},
  track: {height: 4, backgroundColor: '#363636', borderRadius: 2, overflow: 'hidden', marginTop: 12},
  progress: {height: 4, backgroundColor: '#ff453a'},
  error: {color: '#ff7771', fontSize: 12, marginTop: 8},
});
