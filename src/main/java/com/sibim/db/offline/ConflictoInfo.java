package com.sibim.db.offline;

import com.sibim.model.Producto;

/**
 * Holds both versions of a product when SyncService detects that the server
 * was modified by another user while this PC was offline.
 *
 * @param outboxId       row id in product_outbox (used by resolveConflicto to
 *                       mark the row as SYNCED or DISCARDED)
 * @param versionOffline the product as this PC saved it while offline
 * @param versionServidor the product currently on the server (may be null if
 *                        the server record was deleted in the meantime)
 */
public record ConflictoInfo(
        int outboxId,
        Producto versionOffline,
        Producto versionServidor) {}
