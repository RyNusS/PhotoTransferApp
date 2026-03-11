package com.family.phototransfer.ui.receiver;

import android.content.Context;
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

  public ReceiverViewModel_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public ReceiverViewModel get() {
    return newInstance(contextProvider.get());
  }

  public static ReceiverViewModel_Factory create(Provider<Context> contextProvider) {
    return new ReceiverViewModel_Factory(contextProvider);
  }

  public static ReceiverViewModel newInstance(Context context) {
    return new ReceiverViewModel(context);
  }
}
