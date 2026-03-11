package com.family.phototransfer.ui.history;

import com.family.phototransfer.data.db.TransferDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class HistoryViewModel_Factory implements Factory<HistoryViewModel> {
  private final Provider<TransferDao> transferDaoProvider;

  public HistoryViewModel_Factory(Provider<TransferDao> transferDaoProvider) {
    this.transferDaoProvider = transferDaoProvider;
  }

  @Override
  public HistoryViewModel get() {
    return newInstance(transferDaoProvider.get());
  }

  public static HistoryViewModel_Factory create(Provider<TransferDao> transferDaoProvider) {
    return new HistoryViewModel_Factory(transferDaoProvider);
  }

  public static HistoryViewModel newInstance(TransferDao transferDao) {
    return new HistoryViewModel(transferDao);
  }
}
