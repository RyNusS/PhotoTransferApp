package com.family.phototransfer.data.repository;

import com.family.phototransfer.data.db.TransferDao;
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
public final class TransferRepository_Factory implements Factory<TransferRepository> {
  private final Provider<TransferDao> transferDaoProvider;

  public TransferRepository_Factory(Provider<TransferDao> transferDaoProvider) {
    this.transferDaoProvider = transferDaoProvider;
  }

  @Override
  public TransferRepository get() {
    return newInstance(transferDaoProvider.get());
  }

  public static TransferRepository_Factory create(Provider<TransferDao> transferDaoProvider) {
    return new TransferRepository_Factory(transferDaoProvider);
  }

  public static TransferRepository newInstance(TransferDao transferDao) {
    return new TransferRepository(transferDao);
  }
}
