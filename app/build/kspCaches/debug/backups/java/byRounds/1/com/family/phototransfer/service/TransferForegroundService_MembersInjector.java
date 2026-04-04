package com.family.phototransfer.service;

import com.family.phototransfer.network.ReceiverStateHolder;
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
public final class TransferForegroundService_MembersInjector implements MembersInjector<TransferForegroundService> {
  private final Provider<ReceiverStateHolder> receiverStateHolderProvider;

  public TransferForegroundService_MembersInjector(
      Provider<ReceiverStateHolder> receiverStateHolderProvider) {
    this.receiverStateHolderProvider = receiverStateHolderProvider;
  }

  public static MembersInjector<TransferForegroundService> create(
      Provider<ReceiverStateHolder> receiverStateHolderProvider) {
    return new TransferForegroundService_MembersInjector(receiverStateHolderProvider);
  }

  @Override
  public void injectMembers(TransferForegroundService instance) {
    injectReceiverStateHolder(instance, receiverStateHolderProvider.get());
  }

  @InjectedFieldSignature("com.family.phototransfer.service.TransferForegroundService.receiverStateHolder")
  public static void injectReceiverStateHolder(TransferForegroundService instance,
      ReceiverStateHolder receiverStateHolder) {
    instance.receiverStateHolder = receiverStateHolder;
  }
}
