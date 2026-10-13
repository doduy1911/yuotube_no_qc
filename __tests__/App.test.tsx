/**
 * @format
 */

import React from 'react';
import ReactTestRenderer from 'react-test-renderer';
import App from '../App';

jest.mock('react-native-safe-area-context', () =>
  require('react-native-safe-area-context/jest/mock').default,
);
jest.mock('../src/native/specs/NativeVideoExtractor', () => ({
  __esModule: true,
  default: {
    getVideoDetails: jest.fn(),
    openVideo: jest.fn().mockResolvedValue(true),
  },
}));

test('renders correctly', async () => {
  let app: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    app = ReactTestRenderer.create(<App />);
  });

  expect(app!.root.findByProps({ testID: 'home-title' }).props.children).toBe(
    'TubeLite',
  );
  expect(
    app!.root.findByProps({ testID: 'search-input' }).props.placeholder,
  ).toBe('Dán link YouTube để xem video');
  expect(app!.root.findByProps({ testID: 'home-empty-state' })).toBeTruthy();
});
