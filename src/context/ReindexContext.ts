/**
 * Context provided by ReindexScope: identifies the enclosing scope so
 * descendant anchors can carry the scope uid.
 */

import { createContext } from 'react';

export type ReindexContextValue = {
	scopeUid: string;
};

const ReindexContext = createContext<ReindexContextValue | null>(null);

export default ReindexContext;
