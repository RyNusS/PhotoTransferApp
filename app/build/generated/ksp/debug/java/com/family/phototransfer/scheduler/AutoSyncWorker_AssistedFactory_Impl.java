package com.family.phototransfer.scheduler;

import android.content.Context;
import androidx.work.WorkerParameters;
import dagger.internal.DaggerGenerated;
import dagger.internal.InstanceFactory;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class AutoSyncWorker_AssistedFactory_Impl implements AutoSyncWorker_AssistedFactory {
  private final AutoSyncWorker_Factory delegateFactory;

  AutoSyncWorker_AssistedFactory_Impl(AutoSyncWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public AutoSyncWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<AutoSyncWorker_AssistedFactory> create(
      AutoSyncWorker_Factory delegateFactory) {
    return InstanceFactory.create(new AutoSyncWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<AutoSyncWorker_AssistedFactory> createFactoryProvider(
      AutoSyncWorker_Factory delegateFactory) {
    return InstanceFactory.create(new AutoSyncWorker_AssistedFactory_Impl(delegateFactory));
  }
}
