package com.family.phototransfer.scheduler;

import androidx.hilt.work.WorkerAssistedFactory;
import androidx.work.ListenableWorker;
import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.codegen.OriginatingElement;
import dagger.hilt.components.SingletonComponent;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;
import javax.annotation.processing.Generated;

@Generated("androidx.hilt.AndroidXHiltProcessor")
@Module
@InstallIn(SingletonComponent.class)
@OriginatingElement(
    topLevelClass = AutoSyncWorker.class
)
public interface AutoSyncWorker_HiltModule {
  @Binds
  @IntoMap
  @StringKey("com.family.phototransfer.scheduler.AutoSyncWorker")
  WorkerAssistedFactory<? extends ListenableWorker> bind(AutoSyncWorker_AssistedFactory factory);
}
