%header %{
#include "pjsip_sign.h"
%}

%feature("director") SignCallback;
%include pjsip_sign.h

