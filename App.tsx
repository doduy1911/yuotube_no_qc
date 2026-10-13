import React from 'react';
import {
  StatusBar,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import {
  SafeAreaProvider,
  SafeAreaView,
} from 'react-native-safe-area-context';

function App() {
  return (
    <SafeAreaProvider>
      <StatusBar barStyle="light-content" />
      <SafeAreaView style={styles.screen}>
        <View style={styles.header}>
          <View style={styles.brandMark}>
            <View style={styles.playTriangle} />
          </View>
          <Text testID="home-title" style={styles.brandName}>
            TubeLite
          </Text>
          <View style={styles.headerSpacer} />
          <Text style={styles.headerCaption}>BETA</Text>
        </View>

        <View style={styles.content}>
          <Text style={styles.sectionTitle}>Khám phá video</Text>
          <View style={styles.searchBox}>
            <Text style={styles.searchIcon}>⌕</Text>
            <TextInput
              testID="search-input"
              accessibilityLabel="Tìm kiếm video trên YouTube"
              style={styles.searchInput}
              placeholder="Tìm kiếm video trên YouTube"
              placeholderTextColor="#8b8b8b"
              returnKeyType="search"
              selectionColor="#ff453a"
            />
          </View>

          <View testID="home-empty-state" style={styles.emptyState}>
            <View style={styles.emptyIconRing}>
              <View style={styles.emptyPlayTriangle} />
            </View>
            <Text style={styles.emptyTitle}>Video bạn muốn xem</Text>
            <Text style={styles.emptyDescription}>
              Tìm kiếm một chủ đề hoặc dán liên kết video để bắt đầu.
            </Text>
          </View>
        </View>

        <View style={styles.bottomBar}>
          <View style={styles.navItem}>
            <Text style={styles.navIconActive}>⌂</Text>
            <Text style={styles.navLabelActive}>Trang chủ</Text>
          </View>
          <View style={styles.navItem}>
            <Text style={styles.navIcon}>◷</Text>
            <Text style={styles.navLabel}>Thư viện</Text>
          </View>
        </View>
      </SafeAreaView>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#0b0b0b',
  },
  header: {
    height: 62,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    borderBottomColor: '#222222',
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  brandMark: {
    width: 30,
    height: 23,
    borderRadius: 7,
    backgroundColor: '#ff453a',
    alignItems: 'center',
    justifyContent: 'center',
  },
  playTriangle: {
    width: 0,
    height: 0,
    marginLeft: 2,
    borderTopWidth: 5,
    borderBottomWidth: 5,
    borderLeftWidth: 8,
    borderTopColor: 'transparent',
    borderBottomColor: 'transparent',
    borderLeftColor: '#ffffff',
  },
  brandName: {
    marginLeft: 10,
    color: '#f7f7f7',
    fontSize: 20,
    fontWeight: '800',
    letterSpacing: -0.6,
  },
  headerSpacer: {
    flex: 1,
  },
  headerCaption: {
    color: '#ff6259',
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 1.2,
  },
  content: {
    flex: 1,
    paddingHorizontal: 20,
    paddingTop: 24,
  },
  sectionTitle: {
    color: '#f7f7f7',
    fontSize: 22,
    fontWeight: '700',
    letterSpacing: -0.4,
    marginBottom: 18,
  },
  searchBox: {
    height: 52,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 14,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: '#343434',
    backgroundColor: '#191919',
  },
  searchIcon: {
    color: '#a6a6a6',
    fontSize: 27,
    lineHeight: 30,
    marginRight: 10,
  },
  searchInput: {
    flex: 1,
    color: '#f7f7f7',
    fontSize: 14,
    paddingVertical: 0,
  },
  emptyState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 22,
    paddingBottom: 42,
  },
  emptyIconRing: {
    width: 82,
    height: 82,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 41,
    backgroundColor: '#211514',
    borderWidth: 1,
    borderColor: '#46211e',
    marginBottom: 22,
  },
  emptyPlayTriangle: {
    width: 0,
    height: 0,
    marginLeft: 4,
    borderTopWidth: 11,
    borderBottomWidth: 11,
    borderLeftWidth: 17,
    borderTopColor: 'transparent',
    borderBottomColor: 'transparent',
    borderLeftColor: '#ff5147',
  },
  emptyTitle: {
    color: '#f4f4f4',
    fontSize: 17,
    fontWeight: '700',
    marginBottom: 8,
  },
  emptyDescription: {
    color: '#999999',
    fontSize: 13,
    lineHeight: 20,
    textAlign: 'center',
    maxWidth: 290,
  },
  bottomBar: {
    height: 66,
    flexDirection: 'row',
    justifyContent: 'space-around',
    alignItems: 'center',
    borderTopColor: '#222222',
    borderTopWidth: StyleSheet.hairlineWidth,
    backgroundColor: '#101010',
  },
  navItem: {
    minWidth: 90,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 3,
  },
  navIconActive: {
    color: '#ff5147',
    fontSize: 22,
    lineHeight: 24,
  },
  navIcon: {
    color: '#888888',
    fontSize: 21,
    lineHeight: 24,
  },
  navLabelActive: {
    color: '#ff6259',
    fontSize: 10,
    fontWeight: '700',
  },
  navLabel: {
    color: '#888888',
    fontSize: 10,
    fontWeight: '500',
  },
});

export default App;
