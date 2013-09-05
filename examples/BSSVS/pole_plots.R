df <- read.table('4deme_pole.log', header=T)

burninfrac = 0.1
indices <- round(burninfrac*length(df$Sample)):length(df$Sample)

nparams <- (df$migModel.rateMatrixBackward_0_1>0) +
    (df$migModel.rateMatrixBackward_0_2>0) +
    (df$migModel.rateMatrixBackward_0_3>0) +
    (df$migModel.rateMatrixBackward_1_0>0) +
    (df$migModel.rateMatrixBackward_1_2>0) +
    (df$migModel.rateMatrixBackward_1_3>0) +
    (df$migModel.rateMatrixBackward_2_0>0) +
    (df$migModel.rateMatrixBackward_2_1>0) +
    (df$migModel.rateMatrixBackward_2_3>0) +
    (df$migModel.rateMatrixBackward_3_0>0) +
    (df$migModel.rateMatrixBackward_3_1>0) +
    (df$migModel.rateMatrixBackward_3_2>0)
