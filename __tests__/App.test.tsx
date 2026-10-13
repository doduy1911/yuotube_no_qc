/**
 * @format
 */

import React from 'react';
import ReactTestRenderer from 'react-test-renderer';
import App from '../App';

jest.mock('react-native-safe-area-context', () =>
  require('react-native-safe-area-context/jest/mock').default,
);

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
  ).toBe('Tìm kiếm video trên YouTube');
  expect(app!.root.findByProps({ testID: 'home-empty-state' })).toBeTruthy();
});
