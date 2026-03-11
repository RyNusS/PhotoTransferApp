package com.family.phototransfer;

import androidx.hilt.work.HiltWorkerFactory;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class PhotoTransferApp_MembersInjector implements MembersInjector<PhotoTransferApp> {
  private final Provider<HiltWorkerFactory> workerFactoryProvider;

  public PhotoTransferApp_MembersInjector(Provider<HiltWorkerFactory> workerFactoryProvider) {
    this.workerFactoryProvider = workerFactoryProvider;
  }

  public static MembersInjector<PhotoTransferApp> create(
      Provider<HiltWorkerFactory> workerFactoryProvider) {
    return new PhotoTransferApp_MembersInjector(workerFactoryProvider);
  }

  @Override
  public void injectMembers(PhotoTransferApp instance) {
    injectWorkerFactory(instance, workerFactoryProvider.get());
  }

  @InjectedFieldSignature("com.family.phototransfer.PhotoTransferApp.workerFactory")
  public static void injectWorkerFactory(PhotoTransferApp instance,
      HiltWorkerFactory workerFactory) {
    instance.workerFactory = workerFactory;
  }
}
