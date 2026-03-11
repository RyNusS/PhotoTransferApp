package com.family.phototransfer.di;

import com.family.phototransfer.data.db.AppDatabase;
import com.family.phototransfer.data.db.TransferDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class AppModule_ProvideTransferDaoFactory implements Factory<TransferDao> {
  private final Provider<AppDatabase> dbProvider;

  public AppModule_ProvideTransferDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public TransferDao get() {
    return provideTransferDao(dbProvider.get());
  }

  public static AppModule_ProvideTransferDaoFactory create(Provider<AppDatabase> dbProvider) {
    return new AppModule_ProvideTransferDaoFactory(dbProvider);
  }

  public static TransferDao provideTransferDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideTransferDao(db));
  }
}
