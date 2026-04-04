package com.family.phototransfer.data.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class TransferDao_Impl implements TransferDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<TransferRecord> __insertionAdapterOfTransferRecord;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public TransferDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfTransferRecord = new EntityInsertionAdapter<TransferRecord>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `transfer_records` (`id`,`fileName`,`fileHash`,`fileSize`,`sourceDevice`,`status`,`direction`,`transferredAt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TransferRecord entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getFileName());
        statement.bindString(3, entity.getFileHash());
        statement.bindLong(4, entity.getFileSize());
        statement.bindString(5, entity.getSourceDevice());
        statement.bindString(6, entity.getStatus());
        statement.bindString(7, entity.getDirection());
        statement.bindLong(8, entity.getTransferredAt());
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM transfer_records";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final TransferRecord record, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfTransferRecord.insert(record);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<TransferRecord>> getRecentRecords(final long since) {
    final String _sql = "SELECT * FROM transfer_records WHERE transferredAt >= ? ORDER BY transferredAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, since);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"transfer_records"}, new Callable<List<TransferRecord>>() {
      @Override
      @NonNull
      public List<TransferRecord> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "fileName");
          final int _cursorIndexOfFileHash = CursorUtil.getColumnIndexOrThrow(_cursor, "fileHash");
          final int _cursorIndexOfFileSize = CursorUtil.getColumnIndexOrThrow(_cursor, "fileSize");
          final int _cursorIndexOfSourceDevice = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceDevice");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfDirection = CursorUtil.getColumnIndexOrThrow(_cursor, "direction");
          final int _cursorIndexOfTransferredAt = CursorUtil.getColumnIndexOrThrow(_cursor, "transferredAt");
          final List<TransferRecord> _result = new ArrayList<TransferRecord>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TransferRecord _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final String _tmpFileHash;
            _tmpFileHash = _cursor.getString(_cursorIndexOfFileHash);
            final long _tmpFileSize;
            _tmpFileSize = _cursor.getLong(_cursorIndexOfFileSize);
            final String _tmpSourceDevice;
            _tmpSourceDevice = _cursor.getString(_cursorIndexOfSourceDevice);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final String _tmpDirection;
            _tmpDirection = _cursor.getString(_cursorIndexOfDirection);
            final long _tmpTransferredAt;
            _tmpTransferredAt = _cursor.getLong(_cursorIndexOfTransferredAt);
            _item = new TransferRecord(_tmpId,_tmpFileName,_tmpFileHash,_tmpFileSize,_tmpSourceDevice,_tmpStatus,_tmpDirection,_tmpTransferredAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<TransferRecord>> getAllRecords() {
    final String _sql = "SELECT * FROM transfer_records ORDER BY transferredAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"transfer_records"}, new Callable<List<TransferRecord>>() {
      @Override
      @NonNull
      public List<TransferRecord> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "fileName");
          final int _cursorIndexOfFileHash = CursorUtil.getColumnIndexOrThrow(_cursor, "fileHash");
          final int _cursorIndexOfFileSize = CursorUtil.getColumnIndexOrThrow(_cursor, "fileSize");
          final int _cursorIndexOfSourceDevice = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceDevice");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfDirection = CursorUtil.getColumnIndexOrThrow(_cursor, "direction");
          final int _cursorIndexOfTransferredAt = CursorUtil.getColumnIndexOrThrow(_cursor, "transferredAt");
          final List<TransferRecord> _result = new ArrayList<TransferRecord>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TransferRecord _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final String _tmpFileHash;
            _tmpFileHash = _cursor.getString(_cursorIndexOfFileHash);
            final long _tmpFileSize;
            _tmpFileSize = _cursor.getLong(_cursorIndexOfFileSize);
            final String _tmpSourceDevice;
            _tmpSourceDevice = _cursor.getString(_cursorIndexOfSourceDevice);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final String _tmpDirection;
            _tmpDirection = _cursor.getString(_cursorIndexOfDirection);
            final long _tmpTransferredAt;
            _tmpTransferredAt = _cursor.getLong(_cursorIndexOfTransferredAt);
            _item = new TransferRecord(_tmpId,_tmpFileName,_tmpFileHash,_tmpFileSize,_tmpSourceDevice,_tmpStatus,_tmpDirection,_tmpTransferredAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object countByHash(final String hash, final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM transfer_records WHERE fileHash = ? AND fileHash != ''";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, hash);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getSuccessCount(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM transfer_records WHERE status = 'SUCCESS'";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
