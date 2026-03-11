package com.family.phototransfer.ui.upload;

import com.family.phototransfer.network.TransferManager;
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
public final class UploadViewModel_Factory implements Factory<UploadViewModel> {
  private final Provider<TransferManager> transferManagerProvider;

  public UploadViewModel_Factory(Provider<TransferManager> transferManagerProvider) {
    this.transferManagerProvider = transferManagerProvider;
  }

  @Override
  public UploadViewModel get() {
    return newInstance(transferManagerProvider.get());
  }

  public static UploadViewModel_Factory create(Provider<TransferManager> transferManagerProvider) {
    return new UploadViewModel_Factory(transferManagerProvider);
  }

  public static UploadViewModel newInstance(TransferManager transferManager) {
    return new UploadViewModel(transferManager);
  }
}
