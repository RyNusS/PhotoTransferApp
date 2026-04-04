package com.family.phototransfer.ui.receiver;

import android.content.Context;
import com.family.phototransfer.data.repository.TransferRepository;
import com.family.phototransfer.network.ReceiverStateHolder;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class ReceiverViewModel_Factory implements Factory<ReceiverViewModel> {
  private final Provider<Context> contextProvider;

  private final Provider<TransferRepository> repositoryProvider;

  private final Provider<ReceiverStateHolder> receiverStateHolderProvider;

  public ReceiverViewModel_Factory(Provider<Context> contextProvider,
      Provider<TransferRepository> repositoryProvider,
      Provider<ReceiverStateHolder> receiverStateHolderProvider) {
    this.contextProvider = contextProvider;
    this.repositoryProvider = repositoryProvider;
    this.receiverStateHolderProvider = receiverStateHolderProvider;
  }

  @Override
  public ReceiverViewModel get() {
    return newInstance(contextProvider.get(), repositoryProvider.get(), receiverStateHolderProvider.get());
  }

  public static ReceiverViewModel_Factory create(Provider<Context> contextProvider,
      Provider<TransferRepository> repositoryProvider,
      Provider<ReceiverStateHolder> receiverStateHolderProvider) {
    return new ReceiverViewModel_Factory(contextProvider, repositoryProvider, receiverStateHolderProvider);
  }

  public static ReceiverViewModel newInstance(Context context, TransferRepository repository,
      ReceiverStateHolder receiverStateHolder) {
    return new ReceiverViewModel(context, repository, receiverStateHolder);
  }
}
