package com.family.phototransfer.network;

import com.family.phototransfer.data.repository.TransferRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class TransferManager_Factory implements Factory<TransferManager> {
  private final Provider<TransferRepository> repositoryProvider;

  public TransferManager_Factory(Provider<TransferRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public TransferManager get() {
    return newInstance(repositoryProvider.get());
  }

  public static TransferManager_Factory create(Provider<TransferRepository> repositoryProvider) {
    return new TransferManager_Factory(repositoryProvider);
  }

  public static TransferManager newInstance(TransferRepository repository) {
    return new TransferManager(repository);
  }
}
