/**
 * Context provided by ReindexScope: identifies the enclosing scope so
 * descendant anchors can carry the scope uid, and carries the scope's
 * optional explicit ordering priority.
 */

import { createContext } from 'react';

export type ReindexContextValue = {
	scopeUid: string;
	scopeOrder?: number;
};

const ReindexContext = createContext<ReindexContextValue | null>(null);

export default ReindexContext;
