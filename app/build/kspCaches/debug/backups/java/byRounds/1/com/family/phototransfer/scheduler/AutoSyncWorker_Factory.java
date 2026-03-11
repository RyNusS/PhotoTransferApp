package com.family.phototransfer.scheduler;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.family.phototransfer.network.TransferManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class AutoSyncWorker_Factory {
  private final Provider<TransferManager> transferManagerProvider;

  public AutoSyncWorker_Factory(Provider<TransferManager> transferManagerProvider) {
    this.transferManagerProvider = transferManagerProvider;
  }

  public AutoSyncWorker get(Context context, WorkerParameters workerParams) {
    return newInstance(context, workerParams, transferManagerProvider.get());
  }

  public static AutoSyncWorker_Factory create(Provider<TransferManager> transferManagerProvider) {
    return new AutoSyncWorker_Factory(transferManagerProvider);
  }

  public static AutoSyncWorker newInstance(Context context, WorkerParameters workerParams,
      TransferManager transferManager) {
    return new AutoSyncWorker(context, workerParams, transferManager);
  }
}
