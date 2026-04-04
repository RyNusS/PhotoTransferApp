package com.family.phototransfer;

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
public final class MainActivity_MembersInjector implements MembersInjector<MainActivity> {
  private final Provider<ReceiverStateHolder> receiverStateHolderProvider;

  public MainActivity_MembersInjector(Provider<ReceiverStateHolder> receiverStateHolderProvider) {
    this.receiverStateHolderProvider = receiverStateHolderProvider;
  }

  public static MembersInjector<MainActivity> create(
      Provider<ReceiverStateHolder> receiverStateHolderProvider) {
    return new MainActivity_MembersInjector(receiverStateHolderProvider);
  }

  @Override
  public void injectMembers(MainActivity instance) {
    injectReceiverStateHolder(instance, receiverStateHolderProvider.get());
  }

  @InjectedFieldSignature("com.family.phototransfer.MainActivity.receiverStateHolder")
  public static void injectReceiverStateHolder(MainActivity instance,
      ReceiverStateHolder receiverStateHolder) {
    instance.receiverStateHolder = receiverStateHolder;
  }
}
