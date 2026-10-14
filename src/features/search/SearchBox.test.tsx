import React, {useState} from 'react';
import {Text} from 'react-native';
import ReactTestRenderer, {act} from 'react-test-renderer';
import SearchBox from './SearchBox';
import {getSearchSuggestions} from '../../native/SearchSuggestions';

jest.mock('../../native/SearchSuggestions', () => ({getSearchSuggestions: jest.fn()}));

const mockSuggestions = getSearchSuggestions as jest.MockedFunction<typeof getSearchSuggestions>;

function renderSearchBox(onSubmit = jest.fn()) {
  function Harness() {
    const [value, setValue] = useState('');
    return <SearchBox value={value} onChangeText={setValue} onSubmit={onSubmit} />;
  }
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  act(() => { renderer = ReactTestRenderer.create(<Harness />); });
  return {renderer, onSubmit, input: () => renderer.root.findByProps({testID: 'search-input'})};
}

async function settleSuggestions() {
  await act(async () => {
    jest.advanceTimersByTime(300);
    await Promise.resolve();
    await Promise.resolve();
  });
}

beforeEach(() => { jest.useFakeTimers(); mockSuggestions.mockReset(); });
afterEach(() => { jest.useRealTimers(); });

test('does not request suggestions below two trimmed characters', async () => {
  const view = renderSearchBox();
  act(() => view.input().props.onChangeText(' a '));
  await settleSuggestions();
  expect(mockSuggestions).not.toHaveBeenCalled();
});

test('debounces 300ms and renders at most eight unique suggestions', async () => {
  mockSuggestions.mockResolvedValue(['same', 'same', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine']);
  const view = renderSearchBox();
  act(() => view.input().props.onChangeText('em cua'));
  await act(async () => { jest.advanceTimersByTime(299); });
  expect(mockSuggestions).not.toHaveBeenCalled();
  await settleSuggestions();
  expect(mockSuggestions).toHaveBeenCalledWith('em cua');
  expect(view.renderer.root.findAllByType(Text).filter(item => item.props.testID === 'search-suggestion-label')).toHaveLength(8);
});

test('discards an older query response that arrives after the latest one', async () => {
  let resolveOld!: (value: string[]) => void;
  let resolveNew!: (value: string[]) => void;
  mockSuggestions.mockImplementation(query => new Promise(resolve => {
    if (query === 'old query') resolveOld = resolve;
    else resolveNew = resolve;
  }));
  const view = renderSearchBox();
  act(() => view.input().props.onChangeText('old query'));
  await settleSuggestions();
  act(() => view.input().props.onChangeText('new query'));
  await settleSuggestions();
  await act(async () => { resolveNew(['new query suggestion']); await Promise.resolve(); });
  await act(async () => { resolveOld(['old query suggestion']); await Promise.resolve(); });
  expect(view.renderer.root.findByProps({children: 'new query suggestion'})).toBeTruthy();
  expect(view.renderer.root.findAllByProps({children: 'old query suggestion'})).toHaveLength(0);
});

test('clearing input invalidates an in-flight suggestion response', async () => {
  let resolve!: (value: string[]) => void;
  mockSuggestions.mockImplementation(() => new Promise(done => { resolve = done; }));
  const view = renderSearchBox();
  act(() => view.input().props.onChangeText('em cua'));
  await settleSuggestions();
  act(() => view.input().props.onChangeText(''));
  await act(async () => { resolve(['stale suggestion']); await Promise.resolve(); });
  expect(view.renderer.root.findAllByProps({testID: 'search-suggestion'})).toHaveLength(0);
});

test('submitting closes and invalidates suggestions, then submits once', async () => {
  mockSuggestions.mockResolvedValue(['em cua ngay hom qua']);
  const view = renderSearchBox();
  act(() => view.input().props.onChangeText('em cua'));
  await settleSuggestions();
  await act(async () => { view.input().props.onSubmitEditing(); });
  expect(view.onSubmit).toHaveBeenCalledTimes(1);
  expect(view.onSubmit).toHaveBeenCalledWith('em cua');
  expect(view.renderer.root.findAllByProps({testID: 'search-suggestion'})).toHaveLength(0);
});

test('selecting a suggestion fills and submits it exactly once', async () => {
  mockSuggestions.mockResolvedValue(['em cua ngay hom qua son tung']);
  const view = renderSearchBox();
  act(() => view.input().props.onChangeText('em cua'));
  await settleSuggestions();
  await act(async () => { view.renderer.root.findByProps({testID: 'search-suggestion'}).props.onPress(); });
  expect(view.onSubmit).toHaveBeenCalledTimes(1);
  expect(view.onSubmit).toHaveBeenCalledWith('em cua ngay hom qua son tung');
  expect(view.input().props.value).toBe('em cua ngay hom qua son tung');
  await settleSuggestions();
  expect(mockSuggestions).toHaveBeenCalledTimes(1);
  expect(view.renderer.root.findAllByProps({testID: 'search-suggestion-label'})).toHaveLength(0);
});

test('suggestion errors do not block manual search and valid links skip suggestions', async () => {
  mockSuggestions.mockRejectedValue(new Error('offline'));
  const view = renderSearchBox();
  act(() => view.input().props.onChangeText('em cua'));
  await settleSuggestions();
  await act(async () => { view.input().props.onSubmitEditing(); });
  expect(view.onSubmit).toHaveBeenCalledWith('em cua');
  act(() => view.input().props.onChangeText('https://youtu.be/dQw4w9WgXcQ'));
  await settleSuggestions();
  expect(mockSuggestions).toHaveBeenCalledTimes(1);
});
